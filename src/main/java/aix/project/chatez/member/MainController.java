package aix.project.chatez.member;

import org.apache.http.entity.ContentType;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.xcontent.XContentType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.annotation.PreDestroy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

@Controller
public class MainController {
    private final MyServiceRepository myServiceRepository;
    private final ChatEzService chatEzService;
    private final S3Properties s3Properties;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    // 예시: 10개의 스레드를 가진 스레드 풀 생성

    public MainController(MemberRepository memberRepository, MyServiceRepository myServiceRepository, ChatEzService chatEzService, S3Properties s3Properties) {
        this.myServiceRepository = myServiceRepository;
        this.chatEzService = chatEzService;
        this.s3Properties = s3Properties;
    }

    @GetMapping("/service_layout")
    public String service_layout(){return "service/service_layout";}

    @GetMapping("/my_service")
    public String my_service(Model model){
        chatEzService.userServiceDate(model);
        model.addAttribute("bucket", s3Properties.getS3Bucket());
        model.addAttribute("folder",s3Properties.getS3UploadPath());
        return "service/my_service";
    }

    private Path saveTempFile(MultipartFile file) throws IOException {
        // 원본 파일 이름을 얻습니다.
        String originalFilename = file.getOriginalFilename();

        // null 체크 후 임시 디렉토리에 저장할 파일 경로를 생성합니다.
        // 파일 이름이 중복되지 않는 환경이라고 가정합니다.
        Path tempFile = null;
        if (originalFilename != null) {
            tempFile = Files.createTempDirectory("uploads").resolve(originalFilename);
        }
        // MultipartFile의 내용을 임시 파일에 복사합니다.
        if (tempFile != null) {
            file.transferTo(tempFile.toFile());
        }

        return tempFile;
    }

    @PostMapping("/fileUpdate")
    public ResponseEntity<?> handleMultipleFileUpload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("serviceId") String serviceId) throws IOException {

        chatEzService.startUpload(serviceId);
        List<Path> savedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            Path savedFile = saveTempFile(file);
            savedFiles.add(savedFile);
        }
        // 비동기 작업을 위한 Future 생성
        Future<?> future = executorService.submit(() -> {
            try {
                // 파일을 FastAPI 서버에 업데이트하는 로직
                updateToFastApi(serviceId, savedFiles);
                // 임시 파일 삭제
                for (Path savedFile : savedFiles) {
                    Files.deleteIfExists(savedFile);
                }
                chatEzService.completeUpload(serviceId);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error processing files", e);
            }
        });

        try {
            // Future 작업의 완료를 기다립니다.
            future.get(); // 필요하다면 타임아웃을 설정할 수 있습니다.
            Thread.sleep(1000);
            // 비동기 작업이 완료된 후에 파일 데이터를 가져옵니다.
            Map<String, List<Map<String, Object>>> servicesFilesMap = chatEzService.awsFileData();
            // 업데이트된 파일 리스트를 JSON 형태로 클라이언트에 반환합니다.
            return ResponseEntity.ok(servicesFilesMap);
        } catch (Exception e) {
            // 작업 중 오류가 발생한 경우
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error during file processing"));
        }
    }

    @GetMapping("/uploadStatus")
    public ResponseEntity<?> getUploadStatus(@RequestParam String serviceId) {
        boolean isCompleted = chatEzService.isUploadCompleted(serviceId);
        if(isCompleted) {
            return ResponseEntity.ok(Map.of("serviceId", serviceId, "status", "completed"));
        } else {
            return ResponseEntity.ok(Map.of("serviceId", serviceId, "status", "inProgress"));
        }
    }

    @GetMapping("/getFileList")
    public ResponseEntity<?> getFileList() {
        Map<String, List<Map<String, Object>>> servicesFilesMap = chatEzService.awsFileData();
        return ResponseEntity.ok(servicesFilesMap);
    }

    private void updateToFastApi(String serviceId, List<Path> files) {
        // FastAPI 엔드포인트 URL 설정
        String fastApiEndpoint = "http://localhost:8000/update_files";
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // POST 요청 생성
            HttpPost postRequest = new HttpPost(fastApiEndpoint);

            // multipart 엔티티 구성
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            for (Path file : files) {
                builder.addBinaryBody("files", Files.newInputStream(file), ContentType.DEFAULT_BINARY, file.getFileName().toString());
            }
            builder.addTextBody("index", serviceId, ContentType.TEXT_PLAIN);

            // 요청에 엔티티 추가
            HttpEntity entity = builder.build();
            postRequest.setEntity(entity);

            // 요청 실행
            HttpResponse response = httpClient.execute(postRequest);
            // 성공 여부 확인
            if (response.getStatusLine().getStatusCode() != 200) {
                // 에러 처리
                String responseBody = EntityUtils.toString(response.getEntity());
                throw new RuntimeException("실패: HTTP 에러 코드: " + response.getStatusLine().getStatusCode() + " : " + responseBody);
            }
        } catch (IOException e) {
            // 예외 처리
            e.printStackTrace();
        }
    }

    @ResponseBody
    @PostMapping("/upload")
    public String handleFileUpload(@RequestParam("imageFile") MultipartFile imageFile,
                                   @RequestParam("aiName") String aiName,
                                   @RequestParam("aiId") String aiId,
                                   @RequestParam("files") MultipartFile[] files) throws IOException {
        String url =chatEzService.userFileUplaod(imageFile, aiName, aiId);

        List<Path> savedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            Path savedFile = saveTempFile(file);
            savedFiles.add(savedFile);
        }

        executorService.submit(() -> {
            try {
                uploadToFastApi(aiId, savedFiles);
                for (Path savedFile : savedFiles) {
                    Files.deleteIfExists(savedFile);
                }
                chatEzService.activateServiceById(aiId);  // 추가된 코드
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return "redirect:"+url;
    }

    private void uploadToFastApi(String aiId, List<Path> files) {
        String fastApiEndpoint = "http://localhost:8000/upload_files";

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost postRequest = new HttpPost(fastApiEndpoint);

            // Creating multipart entity
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            for (Path file : files) {
                builder.addBinaryBody("files", Files.newInputStream(file), ContentType.MULTIPART_FORM_DATA, file.getFileName().toString());
            }
            builder.addTextBody("index", aiId);

            HttpEntity entity = builder.build();

            postRequest.setEntity(entity);

            HttpResponse response = httpClient.execute(postRequest);
            if (response.getStatusLine().getStatusCode() != 200) {
                // Error handling
                String responseBody = EntityUtils.toString(response.getEntity());
                throw new RuntimeException("Failed with HTTP error code: " + response.getStatusLine().getStatusCode() + " and message: " + responseBody);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @PreDestroy
    public void onDestroy() {
        executorService.shutdown();
    }

    //생성형AI 수정
    @ResponseBody
    @PostMapping("/update")
    public String handleFileUpdate(@RequestParam("updateName") String updateName,
                                   @RequestParam(value="updateFile", required=false) MultipartFile updateFile,
                                   @RequestParam("selectNo") String selectNo) {
        String url = chatEzService.handleFileUpdate(updateName, updateFile, selectNo);

        return "redirect:"+url;
    }

    //생성형AI 삭제
    @ResponseBody
    @PostMapping("/delete")
    public String delete_service(@RequestParam("serviceNo") String serviceNo, Model model) {
        String url = chatEzService.handleDeleteService(serviceNo);
        return "redirect:"+url;
    }
    
    //파일관리 화면
    @GetMapping("/file_manager")
    public String file_manager(Model model) {
        chatEzService.userServiceDate(model);
        Map<String, List<Map<String, Object>>> servicesFilesMap = chatEzService.awsFileData();
        model.addAttribute("servicesFiles", servicesFilesMap);
        model.addAttribute("bucket", s3Properties.getS3Bucket());
        model.addAttribute("folder",s3Properties.getS3UploadPath());
        return "service/file_manager";
    }

}

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
            @RequestParam("serviceId") String serviceId) {
        for (MultipartFile file : files) {
            try {
                String fileContent = new String(file.getBytes(), StandardCharsets.UTF_8);
                String[] sentences = fileContent.split("\\.");

                try (RestHighLevelClient client = OpenSearchClient.createClient()) {
                    for (String sentence : sentences) {
                        Map<String, Object> sentenceDocument = new HashMap<>();
                        sentenceDocument.put("size", file.getSize());
                        sentenceDocument.put("contents", sentence.trim());
                        sentenceDocument.put("name", file.getOriginalFilename());
                        sentenceDocument.put("contentType", file.getContentType());
                        sentenceDocument.put("uploadTime", System.currentTimeMillis());
                        System.out.println("serviceId : "+serviceId);
                        IndexRequest indexRequest = new IndexRequest(serviceId);
                        indexRequest.source(sentenceDocument, XContentType.JSON);
                        client.index(indexRequest, RequestOptions.DEFAULT);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                // JSON 형태의 에러 메시지 반환
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"error\": \"Failed to process file " + file.getOriginalFilename() + "\"}");
            }
        }
        try {
            Map<String, List<Map<String, Object>>> servicesFilesMap = chatEzService.awsFileData();
            return ResponseEntity.ok(servicesFilesMap); // Map을 JSON으로 변환하여 응답
        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = String.format("서비스 ID '%s'에 대한 파일 처리 실패", serviceId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonMap("error", errorMessage));
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

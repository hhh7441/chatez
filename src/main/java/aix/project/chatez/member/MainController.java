package aix.project.chatez.member;

import org.apache.http.entity.ContentType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
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
        Path tempFile = Files.createTempFile("upload", ".tmp");
        file.transferTo(tempFile.toFile());
        return tempFile;
    }

    @ResponseBody
    @PostMapping("/upload")
    public String handleFileUpload(@RequestParam("imageFile") MultipartFile imageFile,
                                   @RequestParam("aiName") String aiName,
                                   @RequestParam("aiId") String aiId,
                                   @RequestParam("files") MultipartFile[] files) throws IOException {
        String url =chatEzService.userFileUplaod(imageFile, aiName, aiId);

        for (MultipartFile file : files) {
            Path savedFile = saveTempFile(file);
            executorService.submit(() -> {
                try {
                    uploadToFastApi(aiId, savedFile);
                    Files.deleteIfExists(savedFile);
                    // 나머지 로직
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        return "redirect:"+url;
    }

    private void uploadToFastApi(String aiId, Path file) {
        String fastApiEndpoint = "http://localhost:8000/upload_files";

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost postRequest = new HttpPost(fastApiEndpoint);

            // Creating multipart entity
            HttpEntity entity = MultipartEntityBuilder.create()
                    .addBinaryBody("files", Files.newInputStream(file), ContentType.MULTIPART_FORM_DATA, file.getFileName().toString())
                    .addTextBody("index", aiId)
                    .build();

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
        chatEzService.awsFileData(model);
        model.addAttribute("bucket", s3Properties.getS3Bucket());
        model.addAttribute("folder",s3Properties.getS3UploadPath());
        return "service/file_manager";
    }

}

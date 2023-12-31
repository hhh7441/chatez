package aix.project.chatez.member;

import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import org.springframework.messaging.simp.SimpMessagingTemplate;


@Service
public class ChatEzService {
    private final MyServiceRepository myServiceRepository;
    private final MemberRepository memberRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    private final AmazonS3 amazonS3;

    @Value("${cloud.aws.s3-bucket}")
    private String bucket;
    @Value("${cloud.aws.s3-upload-path}")
    private String uploadPath;

    @Autowired
    public ChatEzService(MyServiceRepository myServiceRepository, MemberRepository memberRepository, AmazonS3 amazonS3) {
        this.amazonS3 = amazonS3;
        this.myServiceRepository = myServiceRepository;
        this.memberRepository = memberRepository;
    }

    public ResponseEntity<UrlResource> downloadFile(String fileName){
        UrlResource urlResource = new UrlResource(amazonS3.getUrl(String.format("%s/example",bucket), fileName));
        String contentDisposition = String.format("attachment; filename=\\%s\\", URLEncoder.encode(fileName, StandardCharsets.UTF_8));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(urlResource);
    }
    public void userServiceDate(Model model) {
        //로그인 페이지에서 아이디 연동 부분
        List<Member> loginUser = this.memberRepository.findAll();
        if(loginUser.get(0) != null) {
            String name = loginUser.get(0).getName();
            System.out.println("로그인 유저 : " + name);

            Member member = memberRepository.findByName(name);
            Long memberNo = null;
            if (member != null) {
                memberNo = member.getMemberNo();
                System.out.println("id : hhh7441 no : " + memberNo);
            } else {
                System.out.println("hhh7441 회원의 번호를 찾을 수 없습니다.");
            }
            List<MyService> myServices = myServiceRepository.findByMemberMemberNo(memberNo);
            if(!myServices.isEmpty()) {
                System.out.println("no : " + myServices.get(0).getServiceNo());
                System.out.println("name : " + myServices.get(0).getServiceName());
                System.out.println("profilePic : " + myServices.get(0).getProfilePic());
            }
            model.addAttribute("myServices", myServices);
            model.addAttribute("name", name);
        }
    }

    public String userFileUplaod(MultipartFile imageFile, String aiName, String aiId) throws IOException {
        if (imageFile.isEmpty() || StringUtils.isEmpty(aiName)) {
            return "파일 또는 AI의 이름이 없습니다.";
        }

        try {
            String newFileName = s3FileUpload(imageFile);
            List<Member> loginUser = this.memberRepository.findAll();
            String urlValue = aiName+System.currentTimeMillis();
            if (!loginUser.isEmpty()) {
                Member member = loginUser.get(0);
                MyService myService = new MyService();
                myService.setServiceName(aiName);
                myService.setProfilePic(newFileName);
                myService.setServiceId(aiId);
                myService.setUrl(UUID.nameUUIDFromBytes(urlValue.getBytes()).toString().replace("-",""));
                myService.setMember(member);  //엔티티와 엔티티 간의 연결 설정
                myService.setServiceActive(false);
                myServiceRepository.save(myService);
                return "my_service";
            } else {
                return "로그인한 사용자 정보를 찾을 수 없습니다.";
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "my_service";
        }
    }

    private String s3FileUpload(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();

        //업로드 날짜 설정
        String uploadTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String onlyFileName = fileName.substring(0,fileName.lastIndexOf("."));
        String extension = fileName.substring(fileName.lastIndexOf("."));
        String newFileName = String.format("%s_%s%s",onlyFileName, uploadTime, extension);
        //s3 파일 업로드
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());
        String imagePath = String.format("%s/%s",bucket, uploadPath);

        amazonS3.putObject(imagePath, newFileName, file.getInputStream(),metadata);

        return newFileName;
    }

    public String handleFileUpdate(String updateName, MultipartFile updateFile, String selectNo) {
        Long no = Long.parseLong(selectNo);

        if (StringUtils.isEmpty(updateName)) {
            return "업데이트할 이름이 없습니다.";
        }

        try {
            Optional<MyService> optionalMyService = myServiceRepository.findById(no);

            if (optionalMyService.isPresent()) {
                MyService myService = optionalMyService.get();
                myService.setServiceName(updateName);

                // 파일이 있는 경우에만 파일 처리
                if (updateFile != null && !updateFile.isEmpty()) {
                    if (!myService.getProfilePic().isEmpty()) {
                        Path oldFilePath = Paths.get("C:/github/uploaded-images/" + myService.getProfilePic().substring(8)).toAbsolutePath();
                        Files.delete(oldFilePath);
                    }

                    String fileName = updateFile.getOriginalFilename();
                    String fileUploadDirectory = "C:/github/uploaded-images/" + fileName;
                    Path path = Paths.get(fileUploadDirectory).toAbsolutePath();
                    updateFile.transferTo(path.toFile());
                    myService.setProfilePic("/images/" + fileName);
                }

                myServiceRepository.save(myService);

                return "my_service";
            } else {
                return "선택된 번호에 해당하는 레코드를 찾을 수 없습니다.";
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "my_service";
        }
    }

    public String handleDeleteService(String serviceNo) {
        try {
            Long no = Long.parseLong(serviceNo);
            Optional<MyService> optionalMyService = myServiceRepository.findById(no);

            if (optionalMyService.isPresent()) {
                MyService myService = optionalMyService.get();

                if (!myService.getProfilePic().isEmpty()) {
                    //s3에 있는 연결된 파일 delete
                    String imagePath = String.format("%s/%s", uploadPath, myService.getProfilePic());
                    System.out.println("bucket : "+bucket);
                    System.out.println("imagePath : "+imagePath);
                    amazonS3.deleteObject(bucket, imagePath);
                }

                try (RestHighLevelClient client = OpenSearchClient.createClient()) {
                    // 인덱스 삭제
                    DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(myService.getServiceId());
                    client.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
                } catch (IOException e) {
                    e.printStackTrace();
                    // 여기에서 오류 처리를 수행합니다.
                }

                myServiceRepository.deleteById(no);

                return "my_service";
            } else {
                return "my_service";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "my_service";
        }
    }

    @Transactional
    public void activateServiceById(String aiId) {
        MyService myService = myServiceRepository.findByServiceId(aiId);

        if (myService != null) {
            myService.setServiceActive(true);
            System.out.println("aiId : " + aiId);
            messagingTemplate.convertAndSend("/topic/notifications", aiId);
        } else {
            System.out.println("Service with ID: " + aiId + " not found.");
        }
    }

    private final Map<String, Boolean> uploadStatusMap = new ConcurrentHashMap<>();

    public void startUpload(String serviceId) {
        uploadStatusMap.put(serviceId, false);
    }

    public void completeUpload(String serviceId) {
        uploadStatusMap.put(serviceId, true);
    }

    public boolean isUploadCompleted(String serviceId) {
        return uploadStatusMap.getOrDefault(serviceId, false);
    }

    public Map<String, List<Map<String, Object>>> awsFileData() {
        RestHighLevelClient client = OpenSearchClient.createClient();
        List<Member> loginUser = this.memberRepository.findAll();

        if(loginUser.get(0) != null) {
            String name = loginUser.get(0).getName();

            Member member = memberRepository.findByName(name);
            Long memberNo = null;
            if (member != null) {
                memberNo = member.getMemberNo();
                System.out.println("id : hhh7441 no : " + memberNo);
            } else {
                System.out.println("hhh7441 회원의 번호를 찾을 수 없습니다.");
            }

            List<MyService> myServices = myServiceRepository.findByMemberMemberNo(memberNo);
            Map<String, List<Map<String, Object>>> servicesFilesMap = new HashMap<>(); // 각 서비스 이름에 따른 파일 정보를 저장할 맵

            for (MyService myService : myServices) {
                List<Map<String, Object>> files = new ArrayList<>();
                System.out.println("name : " + myService.getServiceName());
                try {
                    GetIndexRequest getIndexRequest = new GetIndexRequest(myService.getServiceId());
                    boolean exists = client.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
                    if (!exists) {
                        // 인덱스가 없는 경우의 처리 로직
                        System.out.println("인덱스가 존재하지 않습니다: " + myService.getServiceId());
                        continue; // 다음 서비스로 넘어갑니다
                    }
                    SearchRequest searchRequest = new SearchRequest(myService.getServiceId()); // 서비스 이름을 인덱스로 사용
                    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

                    // Only fetch the "size", "name", and "contentType" fields
                    String[] includeFields = new String[]{"size", "name", "contentType", "uploadTime"};
                    String[] excludeFields = new String[]{};
                    searchSourceBuilder.fetchSource(includeFields, excludeFields);

                    searchRequest.source(searchSourceBuilder);

                    SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

                    for (SearchHit hit : searchResponse.getHits().getHits()) {
                        Map<String, Object> source = hit.getSourceAsMap();
                        files.add(source);  // 각 hit의 정보를 리스트에 추가합니다.
                        System.out.println(source);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                servicesFilesMap.put(myService.getServiceName(), files);
            }

            try {
                client.close();  // 모든 요청이 끝난 후 클라이언트를 닫습니다.
            } catch (IOException e) {
                e.printStackTrace();
            }
            return servicesFilesMap;  // 서비스 이름에 따른 파일 정보 맵을 Model에 추가합니다.
        }
        return null;
    }
}

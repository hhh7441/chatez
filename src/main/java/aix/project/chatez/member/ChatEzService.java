package aix.project.chatez.member;

import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;



@Service
public class ChatEzService {
    private final MyServiceRepository myServiceRepository;
    private final MemberRepository memberRepository;

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
                    String imagePath = String.format("%s/%s",bucket, uploadPath);
                    amazonS3.deleteObject(imagePath, myService.getProfilePic());
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

    public void awsFileData(Model model) {
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
            model.addAttribute("servicesFiles", servicesFilesMap);  // 서비스 이름에 따른 파일 정보 맵을 Model에 추가합니다.
        }
    }
}

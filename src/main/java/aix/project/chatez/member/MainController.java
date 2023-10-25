package aix.project.chatez.member;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Controller
public class MainController {
    private final MyServiceRepository myServiceRepository;
    private final ChatEzService chatEzService;
    private final S3Properties s3Properties;

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

    @ResponseBody
    @PostMapping("/upload")
    public String handleFileUpload(@RequestParam("imageFile") MultipartFile imageFile,
                                   @RequestParam("aiName") String aiName,
                                   @RequestParam("aiId") String aiId) throws IOException {
        String url =chatEzService.userFileUplaod(imageFile, aiName, aiId);
        return "redirect:"+url;
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
    public String delete_service(@RequestParam("serviceNo") String serviceNo) {
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

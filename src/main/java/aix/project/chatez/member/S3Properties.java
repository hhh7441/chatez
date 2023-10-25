package aix.project.chatez.member;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("cloud.aws")
public class S3Properties {
    private String s3Bucket;
    private String s3UploadPath;
    private String credentialsAccessKey;
    private String credentialsSecreteKey;
    private String regionStatic;
    private String regionAuto;

}

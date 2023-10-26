package aix.project.chatez;

import aix.project.chatez.member.S3Properties;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;


@EnableWebSecurity
@RequiredArgsConstructor
@Configuration
public class SecurityConfig {
    private final S3Properties s3Properties;
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(
                        (authorizeHttpRequests) -> authorizeHttpRequests
                        .requestMatchers(new AntPathRequestMatcher("/ws/**")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/**")).permitAll()
                );
        return http.build();
    }
    @Bean
    public AmazonS3Client amazonS3Client(){
        BasicAWSCredentials credentials = new BasicAWSCredentials(
                s3Properties.getCredentialsAccessKey(),
                s3Properties.getCredentialsSecreteKey()
        );

        return (AmazonS3Client) AmazonS3ClientBuilder
                .standard()
                .withRegion(s3Properties.getRegionStatic())
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .build();
    }

}
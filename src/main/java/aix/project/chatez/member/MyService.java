package aix.project.chatez.member;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class MyService { //로그확인

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_no")
    private Long serviceNo;

    @Column(name = "url", unique = true)
    private String url;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "profile_pic", nullable = false)
    private String profilePic;

    @Column(name = "service_id", nullable = false)
    private String serviceId;

    @Column(name = "service_active", nullable = false)
    private boolean serviceActive;

    @ManyToOne
    @JoinColumn(name = "member_no")
    private Member member;
}

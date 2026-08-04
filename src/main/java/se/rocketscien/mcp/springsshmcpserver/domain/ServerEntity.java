package se.rocketscien.mcp.springsshmcpserver.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "servers")
@Getter
@Setter
public class ServerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private Integer port = 22;

    @Column(nullable = false)
    private String username;

    @Column(columnDefinition = "TEXT")
    private String password;

    @Column(columnDefinition = "TEXT")
    private String privateKey;

    @Column(columnDefinition = "TEXT")
    private String privateKeySecret;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public String getAddress() {
        return username + "@" + host + ":" + port;
    }
}

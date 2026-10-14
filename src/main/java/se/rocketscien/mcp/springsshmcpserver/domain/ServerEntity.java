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

    /**
     * Name of another registered server used as a jump host to reach this one, or {@code null} for a direct
     * connection. Stored as a plain name rather than a reference: the value is the admin's responsibility, and
     * {@link ServerManagementService#resolveChain} resolves it at connect time and reports an unknown name there.
     */
    @Column
    private String connectVia;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public String getAddress() {
        return username + "@" + host + ":" + port;
    }

    /**
     * Derived from the stored secrets, not a column: the entity uses field access ({@code @Id} sits on a
     * field), so this getter is invisible to Hibernate.
     */
    public String getAuthType() {
        if (password != null) {
            return "PASSWORD";
        }
        if (privateKey == null) {
            return "NONE";
        }
        return privateKeySecret != null ? "PRIVATE_KEY_WITH_PASS" : "PRIVATE_KEY";
    }
}

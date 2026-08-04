package ru.rcktsci.experiments.ai.sshmcp.domain;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_tokens")
@Getter
@Setter
public class AuthTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID token;

    @Column(name = "can_edit")
    private Boolean canEdit = false;

    @Column(name = "can_execute")
    private Boolean canExecute = false;

    @Column(name = "execute_only", columnDefinition = "VARCHAR(255)[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] executeOnly = {};

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean canExecuteOnServer(String serverName) {
        if (executeOnly == null || executeOnly.length == 0) {
            return true;
        }
        return Arrays.stream(executeOnly)
                .filter(Objects::nonNull)
                .anyMatch(pattern -> globMatches(pattern, serverName));
    }

    private static boolean globMatches(String pattern, String name) {
        if (!pattern.contains("*")) {
            return pattern.equals(name);
        }
        var regex = "\\Q" + pattern.replace("*", "\\E.*\\Q") + "\\E";
        return Pattern.compile(regex).matcher(name).matches();
    }
}

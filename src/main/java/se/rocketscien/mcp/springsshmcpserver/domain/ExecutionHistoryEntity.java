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
@Table(name = "executions_history")
@Getter
@Setter
public class ExecutionHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String server;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String command;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String result;

    @Column(nullable = false)
    private Integer exitCode;

    @Column(nullable = false)
    private Boolean timedOut = false;

    @Column(nullable = false)
    private LocalDateTime executedAt = LocalDateTime.now();
}

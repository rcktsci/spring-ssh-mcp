package se.rocketscien.mcp.springsshmcpserver.config.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExecuteResult(
        @JsonProperty("stdout") String stdout,
        @JsonProperty("exit_code") int exitCode,
        @JsonProperty("timed_out") boolean timedOut
) {}

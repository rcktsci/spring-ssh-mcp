package se.rocketscien.mcp.springsshmcpserver.config.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErrorResult(
        @JsonProperty("error") String error
) {}

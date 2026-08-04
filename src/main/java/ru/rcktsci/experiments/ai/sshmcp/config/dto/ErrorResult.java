package ru.rcktsci.experiments.ai.sshmcp.config.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErrorResult(
        @JsonProperty("error") String error
) {}
package fr.lepgu.palaisdivin.backend.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.otel")
public record ObservabilityProperties(@NotBlank String tracesEndpoint) {}

package fr.lepgu.palaisdivin.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.ban")
public record BanProperties(
    @NotBlank String baseUrl, @NotNull Duration timeout, @NotNull Duration cacheTtl) {}

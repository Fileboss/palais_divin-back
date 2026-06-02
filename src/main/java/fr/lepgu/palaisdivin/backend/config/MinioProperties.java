package fr.lepgu.palaisdivin.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.minio")
public record MinioProperties(
    @NotBlank String endpoint,
    @NotBlank String accessKey,
    @NotBlank String secretKey,
    @NotNull Duration timeout,
    @NotBlank String bucket,
    @NotNull Duration uploadUrlTtl,
    @NotNull Duration downloadUrlTtl) {}

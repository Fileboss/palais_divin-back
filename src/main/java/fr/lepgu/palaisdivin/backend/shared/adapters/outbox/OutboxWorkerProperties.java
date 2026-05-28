package fr.lepgu.palaisdivin.backend.shared.adapters.outbox;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("palaisdivin.outbox")
@Validated
public record OutboxWorkerProperties(
    @DefaultValue("50") @Min(1) @Max(500) int batchSize,
    @DefaultValue("5") @Min(1) int maxRetries) {}

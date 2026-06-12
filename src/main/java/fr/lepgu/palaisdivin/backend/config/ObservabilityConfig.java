package fr.lepgu.palaisdivin.backend.config;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.otel.bridge.Slf4JEventListener;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnProperty(
    name = "management.tracing.enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityConfig {

  @Bean(destroyMethod = "shutdown")
  SpanExporter otlpSpanExporter(ObservabilityProperties props) {
    return OtlpHttpSpanExporter.builder().setEndpoint(props.tracesEndpoint()).build();
  }

  @Bean(destroyMethod = "close")
  SdkTracerProvider sdkTracerProvider(SpanExporter exporter, Environment env) {
    String appName = env.getProperty("spring.application.name", "application");
    Resource resource =
        Resource.getDefault()
            .merge(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), appName)));
    return SdkTracerProvider.builder()
        .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
        .setResource(resource)
        .build();
  }

  @Bean
  OpenTelemetry openTelemetry(SdkTracerProvider provider) {
    return OpenTelemetrySdk.builder().setTracerProvider(provider).build();
  }

  @Bean
  Tracer micrometerTracer(OpenTelemetry openTelemetry) {
    OtelCurrentTraceContext bridgeContext = new OtelCurrentTraceContext();
    Slf4JEventListener slf4jEventListener = new Slf4JEventListener();
    return new OtelTracer(
        openTelemetry.getTracer("fr.lepgu.palaisdivin.backend"),
        bridgeContext,
        slf4jEventListener::onEvent);
  }
}

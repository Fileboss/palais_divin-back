package fr.lepgu.palaisdivin.backend;

import com.fasterxml.jackson.databind.JsonNode;
import fr.lepgu.palaisdivin.backend.restaurant.adapters.geocoding.BanApiClient;
import fr.lepgu.palaisdivin.backend.restaurant.adapters.geocoding.BanResponse;
import fr.lepgu.palaisdivin.backend.shared.adapters.outbox.Projector;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class SharedTestStubs {

  @Bean
  public BanApiClientStub banApiClientStub() {
    return new BanApiClientStub();
  }

  @Bean
  @Primary
  public BanApiClient primaryBanApiClient(BanApiClientStub stub) {
    return stub;
  }

  @Bean
  public BlockingProjector blockingProjector() {
    return new BlockingProjector();
  }

  @Bean
  public AlwaysFailingProjector alwaysFailingProjector() {
    return new AlwaysFailingProjector();
  }

  public static final class BanApiClientStub implements BanApiClient {

    private static final BanResponse DEFAULT_RESPONSE =
        new BanResponse(
            List.of(
                new BanResponse.Feature(
                    new BanResponse.Geometry(List.of(2.3795, 48.8536)),
                    new BanResponse.Properties(0.96, "80 Rue de Charonne 75011 Paris"))));

    private final AtomicReference<BanResponse> defaultResponse =
        new AtomicReference<>(DEFAULT_RESPONSE);
    private final Map<String, BanResponse> overrides = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> callCounts = new ConcurrentHashMap<>();

    @Override
    public BanResponse search(String query, int limit) {
      callCounts.computeIfAbsent(query, k -> new AtomicInteger()).incrementAndGet();
      BanResponse override = overrides.get(query);
      return override != null ? override : defaultResponse.get();
    }

    public void setDefault(BanResponse response) {
      defaultResponse.set(response);
    }

    public void setResponseFor(String query, BanResponse response) {
      overrides.put(query, response);
    }

    public int callCountFor(String query) {
      AtomicInteger c = callCounts.get(query);
      return c == null ? 0 : c.get();
    }

    public void reset() {
      defaultResponse.set(DEFAULT_RESPONSE);
      overrides.clear();
      callCounts.clear();
    }
  }

  public static final class BlockingProjector implements Projector {

    public final ConcurrentLinkedQueue<String> projectedIds = new ConcurrentLinkedQueue<>();
    public final ConcurrentLinkedQueue<String> projectingThreads = new ConcurrentLinkedQueue<>();

    @Override
    public String aggregateType() {
      return "BlockingAgg";
    }

    @Override
    public void project(String eventType, JsonNode payload) {
      projectingThreads.add(Thread.currentThread().getName());
      projectedIds.add(payload.get("id").asText());
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    public void reset() {
      projectedIds.clear();
      projectingThreads.clear();
    }
  }

  public static final class AlwaysFailingProjector implements Projector {

    public final AtomicInteger callCount = new AtomicInteger();

    @Override
    public String aggregateType() {
      return "FailingAgg";
    }

    @Override
    public void project(String eventType, JsonNode payload) {
      callCount.incrementAndGet();
      throw new RuntimeException("boom");
    }

    public void reset() {
      callCount.set(0);
    }
  }
}

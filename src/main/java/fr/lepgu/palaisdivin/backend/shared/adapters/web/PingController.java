package fr.lepgu.palaisdivin.backend.shared.adapters.web;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PingController {

  @GetMapping("/ping")
  public PingResponse ping() {
    return new PingResponse("ok", Instant.now());
  }
}

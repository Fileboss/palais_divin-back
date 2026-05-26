package fr.lepgu.palaisdivin.backend.shared.adapters.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.json.JsonMapper;
import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PingController.class)
@Import(SecurityConfig.class)
class PingControllerTest {

  @Autowired MockMvc mockMvc;

  @Test
  void ping_returns_ok_with_iso_timestamp() throws Exception {
    var result =
        mockMvc
            .perform(get("/api/v1/public/ping"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.ts").isString())
            .andReturn();

    var json = result.getResponse().getContentAsString();
    var ts = JsonMapper.builder().build().readTree(json).get("ts").asText();
    Instant.parse(ts);
  }
}

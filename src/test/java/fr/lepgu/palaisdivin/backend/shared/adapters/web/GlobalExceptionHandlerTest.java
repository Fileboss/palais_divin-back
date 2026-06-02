package fr.lepgu.palaisdivin.backend.shared.adapters.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.user.domain.OrphanSubjectException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.FailingController.class)
@Import({SecurityConfig.class, GlobalExceptionHandlerTest.FailingController.class})
class GlobalExceptionHandlerTest {

  @RestController
  static class FailingController {
    @GetMapping("/api/v1/public/__test/iae")
    String iae() {
      throw new IllegalArgumentException("bad input");
    }

    @GetMapping("/api/v1/public/__test/boom")
    String boom() {
      throw new RuntimeException("kaboom");
    }

    @GetMapping("/api/v1/public/__test/orphan")
    String orphan() {
      throw new OrphanSubjectException("kc-sub-orphan");
    }
  }

  @Autowired MockMvc mockMvc;

  @Test
  void unknown_path_returns_problem_detail_404() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/does-not-exist"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("Resource not found"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/not-found"));
  }

  @Test
  void illegal_argument_returns_problem_detail_400_with_detail() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/__test/iae"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Bad request"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/bad-request"))
        .andExpect(jsonPath("$.detail").value("bad input"));
  }

  @Test
  void unhandled_exception_returns_problem_detail_500_with_generic_detail() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/__test/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.title").value("Internal server error"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/internal"))
        .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
  }

  @Test
  void orphan_subject_returns_problem_detail_500_without_leaking_subject() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/__test/orphan"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.title").value("Account state inconsistent"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/orphan-subject"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("kc-sub-orphan"))));
  }
}

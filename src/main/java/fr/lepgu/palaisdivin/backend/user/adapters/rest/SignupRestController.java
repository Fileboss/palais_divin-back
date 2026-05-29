package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.ports.SignupUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/signup")
class SignupRestController {

  private final SignupUseCase signupUseCase;

  SignupRestController(SignupUseCase signupUseCase) {
    this.signupUseCase = signupUseCase;
  }

  @PostMapping
  ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest req) {
    User created =
        signupUseCase.signup(req.token(), req.email(), req.displayName(), req.password());
    return ResponseEntity.status(HttpStatus.CREATED).body(SignupResponse.from(created));
  }
}

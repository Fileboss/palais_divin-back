package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import fr.lepgu.palaisdivin.backend.config.InvitationProperties;
import fr.lepgu.palaisdivin.backend.user.domain.model.Invitation;
import fr.lepgu.palaisdivin.backend.user.domain.ports.IssueInvitationUseCase;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/admin/invitations")
class InvitationRestController {

  private final IssueInvitationUseCase issueInvitation;
  private final InvitationProperties properties;

  InvitationRestController(
      IssueInvitationUseCase issueInvitation, InvitationProperties properties) {
    this.issueInvitation = issueInvitation;
    this.properties = properties;
  }

  @PostMapping
  ResponseEntity<InvitationResponse> issue() {
    Invitation issued = issueInvitation.issue();
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(issued.id().value())
            .toUri();
    return ResponseEntity.created(location)
        .body(InvitationResponse.from(issued, properties.signupBaseUrl()));
  }
}

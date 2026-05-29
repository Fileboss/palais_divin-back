package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import fr.lepgu.palaisdivin.backend.user.domain.model.Invitation;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.util.UriComponentsBuilder;

public record InvitationResponse(UUID id, Instant expiresAt, String signupUrl) {

  public static InvitationResponse from(Invitation invitation, String signupBaseUrl) {
    String url =
        UriComponentsBuilder.fromUriString(signupBaseUrl)
            .queryParam("token", invitation.token().value())
            .toUriString();
    return new InvitationResponse(invitation.id().value(), invitation.expiresAt(), url);
  }
}

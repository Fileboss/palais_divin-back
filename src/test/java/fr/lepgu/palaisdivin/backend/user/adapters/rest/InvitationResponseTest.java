package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.user.domain.model.Invitation;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationId;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationToken;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvitationResponseTest {

  private static final Instant CREATED_AT = Instant.parse("2026-05-29T10:00:00Z");
  private static final Instant EXPIRES_AT = CREATED_AT.plusSeconds(48L * 3600L);

  @Test
  void buildsUrlWithTokenQueryParamWhenBaseHasNoQueryString() {
    Invitation invitation = invitationWithToken(new InvitationToken("abc-123"));

    InvitationResponse response = InvitationResponse.from(invitation, "http://host/register");

    assertThat(response.signupUrl()).isEqualTo("http://host/register?token=abc-123");
    assertThat(response.id()).isEqualTo(invitation.id().value());
    assertThat(response.expiresAt()).isEqualTo(EXPIRES_AT);
  }

  @Test
  void appendsTokenWithAmpersandWhenBaseAlreadyHasQueryString() {
    Invitation invitation = invitationWithToken(new InvitationToken("xyz-789"));

    InvitationResponse response =
        InvitationResponse.from(invitation, "http://host/register?foo=bar");

    assertThat(response.signupUrl()).isEqualTo("http://host/register?foo=bar&token=xyz-789");
  }

  @Test
  void roundtripsTokenThroughUrlEncoding() {
    Invitation invitation = invitationWithToken(new InvitationToken("a b/c"));

    InvitationResponse response = InvitationResponse.from(invitation, "http://host/register");

    URI parsed = URI.create(response.signupUrl());
    assertThat(parsed.getRawQuery()).doesNotContain(" ");
    assertThat(parsed.getQuery()).isEqualTo("token=a b/c");
  }

  private static Invitation invitationWithToken(InvitationToken token) {
    return new Invitation(new InvitationId(UUID.randomUUID()), token, EXPIRES_AT, null, CREATED_AT);
  }
}

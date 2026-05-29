package fr.lepgu.palaisdivin.backend.user.domain;

import java.time.Instant;
import java.util.Objects;

public final class InvitationNotUsableException extends RuntimeException {

  public enum Reason {
    EXPIRED,
    ALREADY_CONSUMED
  }

  private final Reason reason;

  private InvitationNotUsableException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }

  public static InvitationNotUsableException expired(Instant expiresAt) {
    Objects.requireNonNull(expiresAt, "expiresAt");
    return new InvitationNotUsableException(Reason.EXPIRED, "Invitation expired at " + expiresAt);
  }

  public static InvitationNotUsableException alreadyConsumed(Instant consumedAt) {
    Objects.requireNonNull(consumedAt, "consumedAt");
    return new InvitationNotUsableException(
        Reason.ALREADY_CONSUMED, "Invitation already consumed at " + consumedAt);
  }
}

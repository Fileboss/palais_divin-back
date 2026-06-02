package fr.lepgu.palaisdivin.backend.user.domain;

public final class OrphanSubjectException extends RuntimeException {

  private final String subject;

  public OrphanSubjectException(String subject) {
    super("Authenticated subject " + subject + " has no app_user row");
    this.subject = subject;
  }

  public String subject() {
    return subject;
  }
}

package fr.lepgu.palaisdivin.backend.shared.adapters.web;

public class AffinityRequiresAuthException extends RuntimeException {
  public AffinityRequiresAuthException() {
    super("Authentication is required for sort=AFFINITY_DESC");
  }
}

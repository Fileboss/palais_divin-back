package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

public class AffinityRequiresAuthException extends RuntimeException {
  public AffinityRequiresAuthException() {
    super("Authentication is required for sort=AFFINITY_DESC");
  }
}

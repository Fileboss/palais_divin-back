package fr.lepgu.palaisdivin.backend.shared.adapters.web;

public class MissingAnchorException extends RuntimeException {
  public MissingAnchorException() {
    super("lat and lng are required when sort is DISTANCE_ASC");
  }
}

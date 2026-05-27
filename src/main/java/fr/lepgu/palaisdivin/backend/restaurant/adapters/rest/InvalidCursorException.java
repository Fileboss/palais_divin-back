package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

public class InvalidCursorException extends RuntimeException {

  public InvalidCursorException() {
    super("invalid cursor");
  }
}

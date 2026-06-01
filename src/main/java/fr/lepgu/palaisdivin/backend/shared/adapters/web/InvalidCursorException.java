package fr.lepgu.palaisdivin.backend.shared.adapters.web;

public class InvalidCursorException extends RuntimeException {

  public InvalidCursorException() {
    super("invalid cursor");
  }
}

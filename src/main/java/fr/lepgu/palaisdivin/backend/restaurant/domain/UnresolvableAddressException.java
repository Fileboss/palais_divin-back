package fr.lepgu.palaisdivin.backend.restaurant.domain;

public final class UnresolvableAddressException extends RuntimeException {

  private final String address;

  public UnresolvableAddressException(String address) {
    super("Address could not be resolved to coordinates: " + address);
    this.address = address;
  }

  public String address() {
    return address;
  }
}

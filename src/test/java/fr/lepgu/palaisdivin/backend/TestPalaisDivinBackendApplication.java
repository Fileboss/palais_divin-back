package fr.lepgu.palaisdivin.backend;

import org.springframework.boot.SpringApplication;

public class TestPalaisDivinBackendApplication {

  public static void main(String[] args) {
    SpringApplication.from(PalaisDivinBackendApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}

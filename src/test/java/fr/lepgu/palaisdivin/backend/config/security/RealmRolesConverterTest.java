package fr.lepgu.palaisdivin.backend.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class RealmRolesConverterTest {

  private final SecurityConfig.RealmRolesConverter converter =
      new SecurityConfig.RealmRolesConverter();

  private static Jwt jwtWithClaims(Map<String, Object> claims) {
    return new Jwt(
        "token-value",
        Instant.now(),
        Instant.now().plusSeconds(60),
        Map.of("alg", "RS256"),
        claims);
  }

  @Test
  void maps_realm_access_roles_to_role_authorities() {
    Jwt jwt = jwtWithClaims(Map.of("realm_access", Map.of("roles", List.of("USER", "ADMIN"))));

    assertThat(converter.convert(jwt))
        .containsExactlyInAnyOrder(
            new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  @Test
  void returns_empty_when_realm_access_missing() {
    Jwt jwt = jwtWithClaims(Map.of("sub", "alice"));
    assertThat(converter.convert(jwt)).isEmpty();
  }

  @Test
  void returns_empty_when_roles_missing() {
    Jwt jwt = jwtWithClaims(Map.of("realm_access", Map.of("name", "palaisdivin")));
    assertThat(converter.convert(jwt)).isEmpty();
  }

  @Test
  void returns_empty_when_roles_not_a_collection() {
    Jwt jwt = jwtWithClaims(Map.of("realm_access", Map.of("roles", "single-string")));
    assertThat(converter.convert(jwt)).isEmpty();
  }

  @Test
  void ignores_non_string_role_entries() {
    Jwt jwt = jwtWithClaims(Map.of("realm_access", Map.of("roles", List.of("USER", 42, true))));
    assertThat(converter.convert(jwt)).containsExactly(new SimpleGrantedAuthority("ROLE_USER"));
  }
}

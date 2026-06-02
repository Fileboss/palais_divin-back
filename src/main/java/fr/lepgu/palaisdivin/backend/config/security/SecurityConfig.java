package fr.lepgu.palaisdivin.backend.config.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationConverter jwtAuthenticationConverter,
      AuthenticationEntryPoint problemDetailEntryPoint,
      AccessDeniedHandler problemDetailAccessDeniedHandler)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/v1/public/**", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/actuator/**", "/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .authenticationEntryPoint(problemDetailEntryPoint)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(problemDetailEntryPoint)
                    .accessDeniedHandler(problemDetailAccessDeniedHandler));
    return http.build();
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new RealmRolesConverter());
    return converter;
  }

  @Bean
  AuthenticationEntryPoint problemDetailEntryPoint(
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
    return (request, response, ex) -> resolver.resolveException(request, response, null, ex);
  }

  @Bean
  AccessDeniedHandler problemDetailAccessDeniedHandler(
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
    return (request, response, ex) -> resolver.resolveException(request, response, null, ex);
  }

  static final class RealmRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
      Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
      if (realmAccess == null) {
        return List.of();
      }
      Object rolesObj = realmAccess.get("roles");
      if (!(rolesObj instanceof Collection<?> rawRoles)) {
        return List.of();
      }
      return rawRoles.stream()
          .filter(String.class::isInstance)
          .map(String.class::cast)
          .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
          .toList();
    }
  }
}

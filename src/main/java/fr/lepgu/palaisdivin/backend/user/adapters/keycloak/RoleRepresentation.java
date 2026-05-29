package fr.lepgu.palaisdivin.backend.user.adapters.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RoleRepresentation(String id, String name) {}

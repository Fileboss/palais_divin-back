package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SignupRequest(
    @NotBlank String token,
    @NotBlank @Email String email,
    @NotBlank String displayName,
    @NotBlank String password) {}

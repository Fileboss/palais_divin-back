package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @NotBlank String token,
    @NotBlank @Email String email,
    @NotBlank @Size(max = 100) String displayName,
    @NotBlank
        @Size(min = 8, max = 128)
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "password must contain at least one letter and one digit")
        String password) {}

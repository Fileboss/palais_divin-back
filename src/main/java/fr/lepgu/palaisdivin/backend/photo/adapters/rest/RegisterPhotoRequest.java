package fr.lepgu.palaisdivin.backend.photo.adapters.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterPhotoRequest(
    @NotBlank @Size(max = 512) String objectKey, @NotBlank @Size(max = 127) String contentType) {}

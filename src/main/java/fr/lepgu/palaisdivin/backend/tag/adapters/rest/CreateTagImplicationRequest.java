package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateTagImplicationRequest(@NotNull UUID tagId, @NotNull UUID impliesTagId) {}

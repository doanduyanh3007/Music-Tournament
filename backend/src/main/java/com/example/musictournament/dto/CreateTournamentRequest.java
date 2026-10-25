package com.example.musictournament.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateTournamentRequest(
    @NotBlank @Size(max=120) String name,
    @NotEmpty @Size(min=2, max=512) List<@NotBlank @Size(max=400) String> songs
) {}

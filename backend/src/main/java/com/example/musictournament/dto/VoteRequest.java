package com.example.musictournament.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record VoteRequest(@NotBlank String voterKey, @NotNull Long songId) {}

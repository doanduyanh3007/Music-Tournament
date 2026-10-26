package com.example.musictournament.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record VoteRequest(
    @NotBlank @Size(max=100) String voterKey,
    @NotNull Long songId,
    @NotBlank(message="Vui lòng nhập tên hiển thị.") @Size(max=50) String displayName,
    @Size(max=1000) String reason
) {}

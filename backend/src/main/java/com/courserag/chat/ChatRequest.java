package com.courserag.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank
        @Size(max = 1000, message = "Question must be 1000 characters or fewer")
        String question
) {}

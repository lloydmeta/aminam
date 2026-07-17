package com.beachape.aminam.app.routes.v1.authc.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Size(max = 255) String username, @NotBlank @Size(max = 72) String password) {}

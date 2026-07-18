package com.beachape.aminam.app.routes.v1.authc.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @NotBlank @Size(min = 3, max = 255) @Pattern(regexp = "^[A-Za-z0-9._@+-]+$") String username,
    @NotBlank @Size(min = 8, max = 72) String password) {}

package com.beachape.aminam.app.routes.v1.databases.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDatabaseRequest(@NotBlank @Size(max = 255) String name) {}

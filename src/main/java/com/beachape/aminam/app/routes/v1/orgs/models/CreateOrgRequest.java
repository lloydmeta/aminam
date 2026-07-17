package com.beachape.aminam.app.routes.v1.orgs.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrgRequest(@NotBlank @Size(max = 255) String name) {}

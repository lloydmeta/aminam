package com.beachape.aminam.app.routes.v1.authc.models;

import org.jspecify.annotations.Nullable;

public record MeResponse(String id, String username, @Nullable String org) {}

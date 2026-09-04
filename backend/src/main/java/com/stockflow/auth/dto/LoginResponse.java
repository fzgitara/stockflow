package com.stockflow.auth.dto;

public record LoginResponse(String token, long expiresInMinutes) {
}

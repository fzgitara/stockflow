package com.stockflow.support;

import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;

import java.util.Map;

/**
 * Thin HTTP helper for integration tests: JSON in/out as Map,
 * optional bearer token, plus register+login convenience.
 */
public class ApiClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {
            };

    private final TestRestTemplate rest;
    private final String base;

    public ApiClient(TestRestTemplate rest) {
        this.rest = rest;
        // TestRestTemplate.getRootUri() = http://localhost:<random port>/
        this.base = rest.getRootUri().replaceAll("/$", "");
    }

    public ResponseEntity<Map<String, Object>> post(String path, Object body, String token) {
        return rest.exchange(base + path, HttpMethod.POST, entity(body, token), MAP);
    }

    public ResponseEntity<Map<String, Object>> put(String path, Object body, String token) {
        return rest.exchange(base + path, HttpMethod.PUT, entity(body, token), MAP);
    }

    public ResponseEntity<Map<String, Object>> get(String path, String token) {
        return rest.exchange(base + path, HttpMethod.GET, entity(null, token), MAP);
    }

    public ResponseEntity<Map<String, Object>> delete(String path, String token) {
        return rest.exchange(base + path, HttpMethod.DELETE, entity(null, token), MAP);
    }

    /** Registers and logs in a fresh user; returns the bearer token. */
    public String registerAndLogin(String email) {
        post("/api/auth/register", Map.of("email", email, "password", "password123"), null);
        ResponseEntity<Map<String, Object>> login =
                post("/api/auth/login", Map.of("email", email, "password", "password123"), null);
        return (String) login.getBody().get("token");
    }

    /** Creates a product; returns its id. */
    @SuppressWarnings("unchecked")
    public String createProduct(String token, String sku, String name, String price, int qty) {
        ResponseEntity<Map<String, Object>> resp = post("/api/products", Map.of(
                "sku", sku, "name", name, "description", "", "unitPrice", price, "quantityOnHand", qty), token);
        if (resp.getStatusCode().value() != 201) {
            throw new IllegalStateException("Product creation failed: " + resp.getBody());
        }
        return (String) resp.getBody().get("id");
    }

    private static HttpEntity<Object> entity(Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(body, headers);
    }
}

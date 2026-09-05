package com.stockflow;

import com.stockflow.support.AbstractIntegrationTest;
import com.stockflow.support.ApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the five mandated scenarios (N4) plus validation, snapshot (V4),
 * draft-only editing (V9) and terminal states (V8).
 */
class StockFlowIntegrationTest extends AbstractIntegrationTest {

    private ApiClient api;
    private String token;

    @BeforeEach
    void setUp() {
        api = client();
        token = api.registerAndLogin(uniqueEmail());
    }

    // --- N4(a): login with a wrong password is rejected ----------------------

    @Test
    @DisplayName("login with wrong password returns 401 with generic message")
    void loginWrongPasswordRejected() {
        ResponseEntity<Map<String, Object>> resp = api.post("/api/auth/login",
                Map.of("email", "nobody@test.local", "password", "wrongpass1"), null);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        // A9: same generic message regardless of whether the user exists
        assertThat(resp.getBody().get("message")).isEqualTo("Invalid email or password");
    }

    // --- N4(b): unauthenticated request to a protected route returns 401 -----

    @Test
    @DisplayName("protected route without token returns 401")
    void protectedRouteWithoutToken() {
        assertThat(api.get("/api/products", null).getStatusCode().value()).isEqualTo(401);
        assertThat(api.get("/api/invoices", null).getStatusCode().value()).isEqualTo(401);
        assertThat(api.post("/api/products", Map.of(), null).getStatusCode().value()).isEqualTo(401);
    }

    // --- Products ------------------------------------------------------------

    @Test
    @DisplayName("product validation: negative price and duplicate sku rejected with field errors")
    void productValidation() {
        ResponseEntity<Map<String, Object>> negative = api.post("/api/products", Map.of(
                "sku", "X-1", "name", "Neg", "unitPrice", "-1", "quantityOnHand", 1), token);
        assertThat(negative.getStatusCode().value()).isEqualTo(400);
        assertThat(fieldErrors(negative)).containsEntry("unitPrice", "Unit price must be >= 0");

        api.createProduct(token, "DUP-1", "First", "10.00", 5);
        ResponseEntity<Map<String, Object>> dup = api.post("/api/products", Map.of(
                "sku", "dup-1", "name", "Second", "unitPrice", "10.00", "quantityOnHand", 5), token);
        assertThat(dup.getStatusCode().value()).isEqualTo(400);
        assertThat(fieldErrors(dup)).containsEntry("sku", "SKU already exists");
    }

    @Test
    @DisplayName("product list supports pagination and search")
    void productListSearchPaginate() {
        for (int i = 1; i <= 3; i++) {
            api.createProduct(token, "PG-00%d".formatted(i), "Widget " + i, "10.00", 10);
        }
        ResponseEntity<Map<String, Object>> page = api.get("/api/products?page=0&size=2", token);
        assertThat(page.getStatusCode().value()).isEqualTo(200);
        List<?> content = (List<?>) page.getBody().get("content");
        assertThat(content).hasSize(2);
        assertThat(page.getBody().get("totalElements")).isEqualTo(3);

        ResponseEntity<Map<String, Object>> search = api.get("/api/products?search=widget 2", token);
        List<?> found = (List<?>) search.getBody().get("content");
        assertThat(found).hasSize(1);
    }

    // --- N4(c): invoicing more than the available stock is rejected ----------

    @Test
    @DisplayName("invoice line exceeding stock is rejected naming the product")
    void oversellRejected() {
        String productId = api.createProduct(token, "ST-1", "Scarce Item", "50.00", 5);
        ResponseEntity<Map<String, Object>> resp = api.post("/api/invoices", Map.of(
                "customerName", "Acme", "issueDate", "2026-09-05", "dueDate", "2026-09-19",
                "items", List.of(Map.of("productId", productId, "quantity", 6))), token);

        assertThat(resp.getStatusCode().value()).isEqualTo(409);
        assertThat((String) resp.getBody().get("message"))
                .contains("Scarce Item").contains("requested 6").contains("available 5");
    }

    // --- N4(d): issuing an invoice decrements stock correctly ----------------

    @Test
    @DisplayName("issuing decrements stock; totals computed server-side with 11% tax")
    void issueDecrementsStock() {
        String productId = api.createProduct(token, "IS-1", "Issued Item", "100.00", 10);
        ResponseEntity<Map<String, Object>> created = api.post("/api/invoices", Map.of(
                "customerName", "Cafe One", "issueDate", "2026-09-05", "dueDate", "2026-09-19",
                "items", List.of(Map.of("productId", productId, "quantity", 3))), token);
        String invoiceId = (String) created.getBody().get("id");

        // V2: server-side totals — 3 x 100.00 = 300.00, tax 11% = 33.00, total 333.00
        assertThat(created.getBody().get("subtotal")).isEqualTo(300.00);
        assertThat(created.getBody().get("taxAmount")).isEqualTo(33.00);
        assertThat(created.getBody().get("total")).isEqualTo(333.00);

        // V5: draft creation must not consume stock
        assertThat(stockOf(productId)).isEqualTo(10);

        ResponseEntity<Map<String, Object>> issued = api.post("/api/invoices/" + invoiceId + "/issue", null, token);
        assertThat(issued.getStatusCode().value()).isEqualTo(200);
        assertThat(stockOf(productId)).isEqualTo(7);
    }

    // --- N4(e): cancelling an issued invoice restores stock -------------------

    @Test
    @DisplayName("cancelling issued invoice restores stock; cancelling draft does not")
    void cancelRestoresStock() {
        String productId = api.createProduct(token, "CN-1", "Restorable Item", "10.00", 10);
        String issuedId = createAndIssue(productId, 4);
        assertThat(stockOf(productId)).isEqualTo(6);

        api.post("/api/invoices/" + issuedId + "/cancel", null, token);
        assertThat(stockOf(productId)).isEqualTo(10); // V7 restore

        // DRAFT cancel restores nothing
        String draftId = createDraft(productId, 2);
        api.post("/api/invoices/" + draftId + "/cancel", null, token);
        assertThat(stockOf(productId)).isEqualTo(10);
    }

    // --- V8: state machine ----------------------------------------------------

    @Test
    @DisplayName("illegal transitions rejected: PAID is terminal; edit only on DRAFT")
    void stateMachineEnforced() {
        String productId = api.createProduct(token, "SM-1", "State Item", "10.00", 50);
        String invoiceId = createAndIssue(productId, 1);

        // edit an ISSUED invoice -> 409 (V9)
        ResponseEntity<Map<String, Object>> edit = api.put("/api/invoices/" + invoiceId, Map.of(
                "customerName", "X", "issueDate", "2026-09-05", "dueDate", "2026-09-19",
                "items", List.of(Map.of("productId", productId, "quantity", 1))), token);
        assertThat(edit.getStatusCode().value()).isEqualTo(409);

        // issue an already-ISSUED invoice -> 409 (V8)
        assertThat(api.post("/api/invoices/" + invoiceId + "/issue", null, token)
                .getStatusCode().value()).isEqualTo(409);

        // PAID is terminal -> cancel after pay is 409 (V8)
        api.post("/api/invoices/" + invoiceId + "/pay", null, token);
        assertThat(api.post("/api/invoices/" + invoiceId + "/cancel", null, token)
                .getStatusCode().value()).isEqualTo(409);
    }

    // --- V4: price snapshot ---------------------------------------------------

    @Test
    @DisplayName("changing a product price does not alter an existing invoice")
    void priceSnapshotImmutable() {
        String productId = api.createProduct(token, "SN-1", "Snapshot Item", "100.00", 10);
        String invoiceId = createAndIssue(productId, 1);
        assertThat(invoiceTotal(invoiceId)).isEqualTo(111.00); // 100 + 11% tax

        api.put("/api/products/" + productId, Map.of(
                "sku", "SN-1", "name", "Snapshot Item", "unitPrice", "999.00", "quantityOnHand", 10), token);

        assertThat(invoiceTotal(invoiceId)).isEqualTo(111.00); // unchanged
    }

    // --- I4: referenced products cannot be deleted ----------------------------

    @Test
    @DisplayName("deleting a product referenced by an invoice returns 409")
    void referencedProductDeleteBlocked() {
        String productId = api.createProduct(token, "DL-1", "Deletable Item", "10.00", 10);
        String invoiceId = createAndIssue(productId, 1);

        ResponseEntity<Map<String, Object>> del = api.delete("/api/products/" + productId, token);
        assertThat(del.getStatusCode().value()).isEqualTo(409);

        // cancel does not remove the reference (soft history), still blocked
        api.post("/api/invoices/" + invoiceId + "/cancel", null, token);
        assertThat(api.delete("/api/products/" + productId, token).getStatusCode().value()).isEqualTo(409);
    }

    // --- A7: ownership ----------------------------------------------------------

    @Test
    @DisplayName("another user's product and invoice resolve as 404")
    void ownershipIsolation() {
        String productId = api.createProduct(token, "OW-1", "Mine Only", "10.00", 5);
        String invoiceId = createAndIssue(productId, 1);

        String otherToken = api.registerAndLogin(uniqueEmail());
        assertThat(api.get("/api/products/" + productId, otherToken).getStatusCode().value()).isEqualTo(404);
        assertThat(api.get("/api/invoices/" + invoiceId, otherToken).getStatusCode().value()).isEqualTo(404);
        assertThat(api.post("/api/invoices/" + invoiceId + "/issue", null, otherToken)
                .getStatusCode().value()).isEqualTo(404);
    }

    // --- helpers ---------------------------------------------------------------

    private int stockOf(String productId) {
        ResponseEntity<Map<String, Object>> resp = api.get("/api/products/" + productId, token);
        return (Integer) resp.getBody().get("quantityOnHand");
    }

    @SuppressWarnings("unchecked")
    private double invoiceTotal(String invoiceId) {
        ResponseEntity<Map<String, Object>> resp = api.get("/api/invoices/" + invoiceId, token);
        return ((Number) resp.getBody().get("total")).doubleValue();
    }

    private String createDraft(String productId, int qty) {
        ResponseEntity<Map<String, Object>> resp = api.post("/api/invoices", Map.of(
                "customerName", "Draft Buyer", "issueDate", "2026-09-05", "dueDate", "2026-09-19",
                "items", List.of(Map.of("productId", productId, "quantity", qty))), token);
        return (String) resp.getBody().get("id");
    }

    private String createAndIssue(String productId, int qty) {
        String id = createDraft(productId, qty);
        ResponseEntity<Map<String, Object>> issued = api.post("/api/invoices/" + id + "/issue", null, token);
        if (issued.getStatusCode().value() != 200) {
            throw new IllegalStateException("Issue failed: " + issued.getBody());
        }
        return id;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> fieldErrors(ResponseEntity<Map<String, Object>> resp) {
        return (Map<String, String>) resp.getBody().get("fieldErrors");
    }
}

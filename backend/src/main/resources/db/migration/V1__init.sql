-- StockFlow initial schema.
-- Schema is owned by Flyway; Hibernate runs with ddl-auto=validate.

CREATE EXTENSION IF NOT EXISTS pgcrypto; -- gen_random_uuid()

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE products (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id),
    sku              VARCHAR(64)  NOT NULL,
    name             VARCHAR(255) NOT NULL,
    description      TEXT,
    unit_price       NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    quantity_on_hand INTEGER      NOT NULL CHECK (quantity_on_hand >= 0),
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id, sku)
);
CREATE INDEX idx_products_user ON products(user_id);

CREATE SEQUENCE invoice_number_seq START 1;

CREATE TABLE invoices (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users(id),
    invoice_number VARCHAR(32)  NOT NULL UNIQUE,
    customer_name  VARCHAR(255) NOT NULL,
    issue_date     DATE         NOT NULL,
    due_date       DATE         NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'
                   CHECK (status IN ('DRAFT', 'ISSUED', 'PAID', 'CANCELLED')),
    notes          TEXT,
    subtotal       NUMERIC(12,2) NOT NULL,
    tax_amount     NUMERIC(12,2) NOT NULL,
    total          NUMERIC(12,2) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_invoices_user_status ON invoices(user_id, status);

CREATE TABLE invoice_items (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id   UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    product_id   UUID NOT NULL REFERENCES products(id), -- no ON DELETE: referenced products cannot be hard-deleted
    product_name VARCHAR(255) NOT NULL,                 -- snapshot at invoice time
    unit_price   NUMERIC(12,2) NOT NULL,                -- snapshot at invoice time
    quantity     INTEGER      NOT NULL CHECK (quantity > 0),
    line_total   NUMERIC(12,2) NOT NULL
);
CREATE INDEX idx_items_invoice ON invoice_items(invoice_id);

CREATE TABLE cola_records (
    id              BIGSERIAL PRIMARY KEY,
    ttb_id          VARCHAR(50)  NOT NULL UNIQUE,
    brand_name      VARCHAR(500),
    fanciful_name   VARCHAR(500),
    class_type      VARCHAR(300),
    applicant_name  VARCHAR(500),
    approval_date   DATE,
    status          VARCHAR(50),
    label_image_url TEXT,
    series_id       VARCHAR(50),
    beverage_type   VARCHAR(100),
    raw_json        TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_cola_ttb_id        ON cola_records(ttb_id);
CREATE INDEX idx_cola_status        ON cola_records(status);
CREATE INDEX idx_cola_approval_date ON cola_records(approval_date DESC);
CREATE INDEX idx_cola_beverage_type ON cola_records(beverage_type);

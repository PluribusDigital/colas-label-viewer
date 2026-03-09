ALTER TABLE cola_records
    ADD COLUMN search_vector TSVECTOR
        GENERATED ALWAYS AS (
            setweight(to_tsvector('english', coalesce(brand_name,     '')), 'A') ||
            setweight(to_tsvector('english', coalesce(fanciful_name,  '')), 'B') ||
            setweight(to_tsvector('english', coalesce(class_type,     '')), 'C') ||
            setweight(to_tsvector('english', coalesce(applicant_name, '')), 'C')
        ) STORED;

CREATE INDEX idx_cola_fts ON cola_records USING GIN(search_vector);

package gov.treas.ttb.cola.viewer.model;

import java.time.Instant;

public record IngestResult(int recordsProcessed, Instant completedAt) {}

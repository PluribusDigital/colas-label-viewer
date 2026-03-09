package gov.treas.ttb.cola.viewer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.treas.ttb.cola.viewer.model.ColaRecord;
import gov.treas.ttb.cola.viewer.model.IngestResult;
import gov.treas.ttb.cola.viewer.model.SocrataRecord;
import gov.treas.ttb.cola.viewer.repository.ColaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class ColaIngestionService {

    static final String RESOURCE_PATH = "/resource/n48b-hpqm.json";
    static final String TTB_IMAGE_BASE = "https://www.ttb.gov";
    static final int PAGE_SIZE = 1000;

    private final WebClient socrataWebClient;
    private final ColaRepository colaRepository;
    private final ObjectMapper objectMapper;

    public ColaIngestionService(
            @Qualifier("socrataWebClient") WebClient socrataWebClient,
            ColaRepository colaRepository,
            ObjectMapper objectMapper) {
        this.socrataWebClient = socrataWebClient;
        this.colaRepository = colaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IngestResult ingestAll() {
        log.info("Starting COLA ingestion");
        int offset = 0;
        int total = 0;
        List<SocrataRecord> page;
        do {
            page = fetchPage(offset);
            page.stream()
                    .map(this::toEntity)
                    .filter(Objects::nonNull)
                    .forEach(colaRepository::upsert);
            total += page.size();
            offset += PAGE_SIZE;
            log.debug("Ingested {} records so far", total);
        } while (page.size() == PAGE_SIZE);
        log.info("Ingestion complete. Total records: {}", total);
        return new IngestResult(total, Instant.now());
    }

    List<SocrataRecord> fetchPage(int offset) {
        return socrataWebClient.get()
                .uri(b -> b.path(RESOURCE_PATH)
                           .queryParam("$limit", PAGE_SIZE)
                           .queryParam("$offset", offset)
                           .queryParam("$order", "ttb_id ASC")
                           .build())
                .retrieve()
                .bodyToFlux(SocrataRecord.class)
                .collectList()
                .block(Duration.ofSeconds(30));
    }

    ColaRecord toEntity(SocrataRecord sr) {
        if (sr.ttbId() == null || sr.ttbId().isBlank()) {
            log.warn("Skipping record with null/blank ttb_id");
            return null;
        }
        ColaRecord r = new ColaRecord();
        r.setTtbId(sr.ttbId().strip());
        r.setBrandName(normalize(sr.brandName()));
        r.setFancifulName(normalize(sr.fancifulName()));
        r.setClassType(normalize(sr.classTypeDescription()));
        r.setApplicantName(normalize(sr.applicantName()));
        r.setApprovalDate(parseDate(sr.approvalDate()));
        r.setStatus(normalize(sr.status()));
        r.setLabelImageUrl(buildImageUrl(sr.labelImageFile()));
        r.setSeriesId(normalize(sr.seriesId()));
        r.setBeverageType(normalize(sr.beverageType()));
        try {
            r.setRawJson(objectMapper.writeValueAsString(sr));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize raw JSON for ttb_id={}", sr.ttbId());
        }
        return r;
    }

    static String normalize(String value) {
        if (value == null) return null;
        String stripped = value.strip();
        if (stripped.isEmpty() || stripped.equalsIgnoreCase("N/A")) return null;
        return stripped;
    }

    static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"))
                    .toLocalDate();
        } catch (DateTimeParseException e1) {
            try {
                return LocalDate.parse(raw.substring(0, 10));
            } catch (DateTimeParseException | IndexOutOfBoundsException e2) {
                log.warn("Unparseable date: '{}'", raw);
                return null;
            }
        }
    }

    static String buildImageUrl(String labelImageFile) {
        if (labelImageFile == null || labelImageFile.isBlank()) return null;
        String cleaned = labelImageFile.strip();
        return cleaned.startsWith("http") ? cleaned : TTB_IMAGE_BASE + cleaned;
    }
}

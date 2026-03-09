package gov.treas.ttb.cola.viewer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.treas.ttb.cola.viewer.model.ColaRecord;
import gov.treas.ttb.cola.viewer.model.IngestResult;
import gov.treas.ttb.cola.viewer.repository.ColaRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ColaIngestionService {

    static final String SEARCH_PAGE   = "/colasonline/publicSearchColasBasic.do";
    static final String SEARCH_SUBMIT = "/colasonline/publicSearchColasBasicProcess.do";
    static final String CSV_DOWNLOAD  = "/colasonline/publicSaveSearchResultsToFile.do";
    static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final WebClient ttbClient;
    private final ColaRepository colaRepository;
    private final ObjectMapper objectMapper;

    @Value("${ttb.online.base-url}")
    private String ttbOnlineBaseUrl;

    @Value("${ttb.online.ingest-from-date:2022-01-01}")
    private String ingestFromDate;

    public ColaIngestionService(
            @Qualifier("ttbOnlineWebClient") WebClient ttbClient,
            ColaRepository colaRepository,
            ObjectMapper objectMapper) {
        this.ttbClient = ttbClient;
        this.colaRepository = colaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IngestResult ingestAll() {
        LocalDate from = LocalDate.parse(ingestFromDate);
        LocalDate to   = LocalDate.now();
        log.info("Starting COLA ingest from {} to {}", from, to);

        int total = 0;
        int skipped = 0;
        LocalDate chunkStart = from;
        while (chunkStart.isBefore(to)) {
            LocalDate chunkEnd = chunkStart.plusMonths(3).minusDays(1);
            if (chunkEnd.isAfter(to)) chunkEnd = to;

            try {
                String csv = fetchCsvChunk(chunkStart, chunkEnd);
                List<ColaRecord> records = parseCsv(csv);
                records.forEach(colaRepository::upsert);
                total += records.size();
                log.debug("Chunk {}/{}: {} records (running total: {})", chunkStart, chunkEnd, records.size(), total);
            } catch (Exception e) {
                log.warn("Skipping chunk {}/{} due to error: {}", chunkStart, chunkEnd, e.getMessage());
                skipped++;
            }

            chunkStart = chunkEnd.plusDays(1);
        }

        if (skipped > 0) log.warn("Ingest finished with {} skipped chunk(s). Records upserted: {}", skipped, total);
        else log.info("Ingest complete. Total records upserted: {}", total);
        return new IngestResult(total, Instant.now());
    }

    public String fetchCsvChunk(LocalDate from, LocalDate to) {
        // Step 1: GET the search page — capture Set-Cookie headers for the session
        ResponseEntity<String> initResp = ttbClient.get()
                .uri(SEARCH_PAGE)
                .retrieve()
                .toEntity(String.class)
                .block(Duration.ofSeconds(15));

        Map<String, String> cookies = parseCookies(initResp);

        // Step 2: POST the search form, forwarding the session cookie; capture updated cookies
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("searchCriteria.dateCompletedFrom",    from.format(MDY));
        form.add("searchCriteria.dateCompletedTo",      to.format(MDY));
        form.add("searchCriteria.productOrFancifulName", "");
        form.add("searchCriteria.productNameSearchType", "B");
        form.add("searchCriteria.classTypeFrom",        "");
        form.add("searchCriteria.classTypeTo",          "");
        form.add("searchCriteria.originCode",           "");

        ResponseEntity<String> searchResp = ttbClient.post()
                .uri(SEARCH_SUBMIT + "?action=search")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Cookie", cookieString(cookies))
                .header("Referer", ttbOnlineBaseUrl + SEARCH_PAGE)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .toEntity(String.class)
                .block(Duration.ofSeconds(30));

        // Merge any updated cookies from POST response (e.g. rotated TS tokens)
        cookies.putAll(parseCookies(searchResp));

        // Step 3: Download the CSV of all results (up to 1000), reusing merged session cookies
        return ttbClient.get()
                .uri(CSV_DOWNLOAD + "?path=/publicSearchColasBasicProcess")
                .header("Cookie", cookieString(cookies))
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(30));
    }

    /** Parses Set-Cookie headers into a name→value map (later entries win). */
    private static Map<String, String> parseCookies(ResponseEntity<?> response) {
        Map<String, String> map = new LinkedHashMap<>();
        if (response == null) return map;
        List<String> setCookies = response.getHeaders().get("Set-Cookie");
        if (setCookies == null) return map;
        for (String raw : setCookies) {
            String nameValue = raw.split(";")[0].strip();
            int eq = nameValue.indexOf('=');
            if (eq > 0) map.put(nameValue.substring(0, eq), nameValue.substring(eq + 1));
        }
        return map;
    }

    /** Serialises a cookie map to a Cookie header value. */
    private static String cookieString(Map<String, String> cookies) {
        StringBuilder sb = new StringBuilder();
        cookies.forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(k).append('=').append(v);
        });
        return sb.toString();
    }

    public List<ColaRecord> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<ColaRecord> results = new ArrayList<>();
        try (CSVParser parser = CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .build()
                .parse(new StringReader(csv))) {

            for (CSVRecord row : parser) {
                ColaRecord r = rowToEntity(row);
                if (r != null) results.add(r);
            }
        } catch (Exception e) {
            log.warn("CSV parse error: {}", e.getMessage());
        }
        return results;
    }

    ColaRecord rowToEntity(CSVRecord row) {
        String rawTtbId = get(row, "TTB ID");
        String ttbId = stripTtbIdApostrophe(rawTtbId);
        if (ttbId == null || ttbId.isBlank()) {
            log.warn("Skipping row with blank TTB ID");
            return null;
        }

        String classTypeDesc = normalize(get(row, "Class/Type Desc"));
        ColaRecord r = new ColaRecord();
        r.setTtbId(ttbId);
        r.setBrandName(normalize(get(row, "Brand Name")));
        r.setFancifulName(normalize(get(row, "Fanciful Name")));
        r.setClassType(classTypeDesc);
        r.setApplicantName(normalize(get(row, "Origin Desc")));
        r.setApprovalDate(parseMdyDate(get(row, "Completed Date")));
        r.setStatus("APPROVED");
        r.setSeriesId(normalize(get(row, "Permit No.")));
        r.setBeverageType(deriveBeverageType(classTypeDesc));
        r.setLabelImageUrl(null);

        try {
            r.setRawJson(objectMapper.writeValueAsString(Map.of(
                    "ttbId",      ttbId,
                    "permitNo",   nvl(get(row, "Permit No.")),
                    "serialNo",   nvl(get(row, "Serial Number")),
                    "classCode",  nvl(get(row, "Class/Type")),
                    "originCode", nvl(get(row, "Origin"))
            )));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize raw JSON for ttb_id={}", ttbId);
        }
        return r;
    }

    private static String get(CSVRecord row, String col) {
        try { return row.get(col); } catch (IllegalArgumentException e) { return null; }
    }

    private static String nvl(String s) { return s != null ? s : ""; }

    static String stripTtbIdApostrophe(String raw) {
        if (raw == null) return null;
        String s = raw.strip();
        if (s.startsWith("'")) s = s.substring(1);
        if (s.endsWith("'"))   s = s.substring(0, s.length() - 1);
        return s.isBlank() ? null : s;
    }

    static String normalize(String value) {
        if (value == null) return null;
        String s = value.strip();
        if (s.isEmpty() || s.equalsIgnoreCase("N/A")) return null;
        return s;
    }

    static LocalDate parseMdyDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.strip(), MDY);
        } catch (DateTimeParseException e) {
            log.warn("Unparseable date: '{}'", raw);
            return null;
        }
    }

    static String deriveBeverageType(String classTypeDesc) {
        if (classTypeDesc == null) return null;
        String desc = classTypeDesc.toUpperCase();
        if (desc.contains("WINE") || desc.contains("CHAMPAGNE")
                || desc.contains("CIDER") || desc.contains("SAKE") || desc.contains("MEAD")) {
            return "WINE";
        }
        if (desc.contains("BEER") || desc.contains("ALE") || desc.contains("LAGER")
                || desc.contains("STOUT") || desc.contains("PORTER")
                || desc.contains("MALT BEVER")) {
            return "MALT BEVERAGES";
        }
        return "DISTILLED SPIRITS";
    }
}

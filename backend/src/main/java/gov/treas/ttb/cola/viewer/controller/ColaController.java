package gov.treas.ttb.cola.viewer.controller;

import gov.treas.ttb.cola.viewer.model.ColaDto;
import gov.treas.ttb.cola.viewer.model.IngestResult;
import gov.treas.ttb.cola.viewer.model.PagedResponse;
import gov.treas.ttb.cola.viewer.service.ColaIngestionService;
import gov.treas.ttb.cola.viewer.service.ColaSearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class ColaController {

    private final ColaSearchService searchService;
    private final ColaIngestionService ingestionService;

    @GetMapping("/colas")
    public ResponseEntity<PagedResponse<ColaDto>> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) String beverageType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(searchService.search(q, beverageType, status, page, size));
    }

    @GetMapping("/colas/{ttbId}")
    public ResponseEntity<ColaDto> getById(@PathVariable String ttbId) {
        return searchService.findByTtbId(ttbId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/colas/filters/beverage-types")
    public List<String> getBeverageTypes() {
        return searchService.getBeverageTypes();
    }

    @PostMapping("/admin/ingest")
    public ResponseEntity<IngestResult> triggerIngest() {
        return ResponseEntity.accepted().body(ingestionService.ingestAll());
    }
}

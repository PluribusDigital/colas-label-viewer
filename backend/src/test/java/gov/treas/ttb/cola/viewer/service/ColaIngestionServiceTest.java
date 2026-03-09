package gov.treas.ttb.cola.viewer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.treas.ttb.cola.viewer.model.ColaRecord;
import gov.treas.ttb.cola.viewer.repository.ColaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static gov.treas.ttb.cola.viewer.service.ColaIngestionService.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ColaIngestionServiceTest {

    @Mock ColaRepository colaRepository;

    ColaIngestionService service;

    @BeforeEach
    void setUp() {
        service = new ColaIngestionService(null, colaRepository, new ObjectMapper());
    }

    // ── stripTtbIdApostrophe ──────────────────────────────────────────────────

    @Test void stripTtbId_null_returnsNull() { assertThat(stripTtbIdApostrophe(null)).isNull(); }
    @Test void stripTtbId_blank_returnsNull() { assertThat(stripTtbIdApostrophe("  ")).isNull(); }

    @Test void stripTtbId_leadingApostrophe_stripped() {
        assertThat(stripTtbIdApostrophe("'24009001000244")).isEqualTo("24009001000244");
    }
    @Test void stripTtbId_noApostrophe_unchanged() {
        assertThat(stripTtbIdApostrophe("24009001000244")).isEqualTo("24009001000244");
    }
    @Test void stripTtbId_leadingZeros_preserved() {
        assertThat(stripTtbIdApostrophe("'03211001000018")).isEqualTo("03211001000018");
    }

    // ── normalize ─────────────────────────────────────────────────────────────

    @Test void normalize_null_returnsNull() { assertThat(normalize(null)).isNull(); }
    @Test void normalize_blank_returnsNull() { assertThat(normalize("  ")).isNull(); }
    @Test void normalize_na_returnsNull() { assertThat(normalize("N/A")).isNull(); }
    @Test void normalize_whitespace_stripped() {
        assertThat(normalize("  BUFFALO TRACE  ")).isEqualTo("BUFFALO TRACE");
    }

    // ── parseMdyDate ──────────────────────────────────────────────────────────

    @Test void parseMdyDate_null_returnsNull() { assertThat(parseMdyDate(null)).isNull(); }
    @Test void parseMdyDate_blank_returnsNull() { assertThat(parseMdyDate("")).isNull(); }
    @Test void parseMdyDate_valid_parsed() {
        assertThat(parseMdyDate("03/20/2020")).isEqualTo(LocalDate.of(2020, 3, 20));
    }
    @Test void parseMdyDate_malformed_returnsNull() {
        assertThat(parseMdyDate("2020-03-20")).isNull();
    }

    // ── deriveBeverageType ────────────────────────────────────────────────────

    @Test void deriveBeverageType_null_returnsNull() { assertThat(deriveBeverageType(null)).isNull(); }

    @Test void deriveBeverageType_wine_detected() {
        assertThat(deriveBeverageType("TABLE RED WINE")).isEqualTo("WINE");
        assertThat(deriveBeverageType("CHAMPAGNE")).isEqualTo("WINE");
        assertThat(deriveBeverageType("HARD CIDER")).isEqualTo("WINE");
    }
    @Test void deriveBeverageType_beer_detected() {
        assertThat(deriveBeverageType("DOMESTIC ALE")).isEqualTo("MALT BEVERAGES");
        assertThat(deriveBeverageType("AMERICAN LAGER")).isEqualTo("MALT BEVERAGES");
    }
    @Test void deriveBeverageType_spirits_default() {
        assertThat(deriveBeverageType("STRAIGHT BOURBON WHISKY")).isEqualTo("DISTILLED SPIRITS");
        assertThat(deriveBeverageType("BRANDY")).isEqualTo("DISTILLED SPIRITS");
    }

    // ── parseCsv ─────────────────────────────────────────────────────────────

    static final String SAMPLE_CSV = """
            TTB ID,Permit No.,Serial Number,Completed Date,Fanciful Name,Brand Name,Origin,Origin Desc,Class/Type,Class/Type Desc
            '24009001000244,DSP-KY-113,240013,02/09/2024,SBS,BUFFALO TRACE,22,KENTUCKY,101,STRAIGHT BOURBON WHISKY
            '03211001000018,BW-MI-159,030035,12/18/2025,CASCADE VAL,CASCADE WINERY,06,MICHIGAN,80,TABLE RED WINE
            ,DSP-KY-113,BADROW,01/01/2024,,BLANK ID,22,KENTUCKY,101,STRAIGHT BOURBON WHISKY
            """;

    @Test void parseCsv_validRows_mapped() {
        assertThat(service.parseCsv(SAMPLE_CSV)).hasSize(2);  // blank-TTB-ID row skipped
    }

    @Test void parseCsv_spiritsRecord_fieldsCorrect() {
        ColaRecord r = service.parseCsv(SAMPLE_CSV).get(0);
        assertThat(r.getTtbId()).isEqualTo("24009001000244");
        assertThat(r.getBrandName()).isEqualTo("BUFFALO TRACE");
        assertThat(r.getFancifulName()).isEqualTo("SBS");
        assertThat(r.getClassType()).isEqualTo("STRAIGHT BOURBON WHISKY");
        assertThat(r.getApplicantName()).isEqualTo("KENTUCKY");
        assertThat(r.getApprovalDate()).isEqualTo(LocalDate.of(2024, 2, 9));
        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(r.getSeriesId()).isEqualTo("DSP-KY-113");
        assertThat(r.getBeverageType()).isEqualTo("DISTILLED SPIRITS");
        assertThat(r.getLabelImageUrl()).isNull();
    }

    @Test void parseCsv_wineRecord_beverageTypeWine() {
        assertThat(service.parseCsv(SAMPLE_CSV).get(1).getBeverageType()).isEqualTo("WINE");
    }

    @Test void parseCsv_blankTtbId_rowSkipped() {
        assertThat(service.parseCsv(SAMPLE_CSV))
                .noneMatch(r -> r.getTtbId() == null || r.getTtbId().isBlank());
    }

    @Test void parseCsv_nullOrEmpty_returnsEmptyList() {
        assertThat(service.parseCsv("")).isEmpty();
        assertThat(service.parseCsv(null)).isEmpty();
    }
}

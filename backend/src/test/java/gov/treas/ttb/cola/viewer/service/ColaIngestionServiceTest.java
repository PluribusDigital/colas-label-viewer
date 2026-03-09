package gov.treas.ttb.cola.viewer.service;

import gov.treas.ttb.cola.viewer.model.ColaRecord;
import gov.treas.ttb.cola.viewer.model.SocrataRecord;
import gov.treas.ttb.cola.viewer.repository.ColaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

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

    // normalize()

    @Test
    void normalize_null_returnsNull() {
        assertThat(normalize(null)).isNull();
    }

    @Test
    void normalize_blank_returnsNull() {
        assertThat(normalize("   ")).isNull();
    }

    @Test
    void normalize_emptyString_returnsNull() {
        assertThat(normalize("")).isNull();
    }

    @Test
    void normalize_nA_uppercase_returnsNull() {
        assertThat(normalize("N/A")).isNull();
    }

    @Test
    void normalize_nA_lowercase_returnsNull() {
        assertThat(normalize("n/a")).isNull();
    }

    @Test
    void normalize_nA_mixedCase_returnsNull() {
        assertThat(normalize("N/a")).isNull();
    }

    @Test
    void normalize_leadingTrailingWhitespace_isStripped() {
        assertThat(normalize("  Buffalo Trace  ")).isEqualTo("Buffalo Trace");
    }

    @Test
    void normalize_validValue_returnsValue() {
        assertThat(normalize("BUFFALO TRACE")).isEqualTo("BUFFALO TRACE");
    }

    // parseDate()

    @Test
    void parseDate_null_returnsNull() {
        assertThat(parseDate(null)).isNull();
    }

    @Test
    void parseDate_blank_returnsNull() {
        assertThat(parseDate("  ")).isNull();
    }

    @Test
    void parseDate_socrataIsoDatetime_parsed() {
        assertThat(parseDate("2024-01-15T00:00:00.000")).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    void parseDate_isoDate_parsed() {
        assertThat(parseDate("2020-06-30")).isEqualTo(LocalDate.of(2020, 6, 30));
    }

    @Test
    void parseDate_malformed_returnsNull() {
        assertThat(parseDate("01/15/2024")).isNull();
    }

    @Test
    void parseDate_randomString_returnsNull() {
        assertThat(parseDate("not-a-date")).isNull();
    }

    // buildImageUrl()

    @Test
    void buildImageUrl_null_returnsNull() {
        assertThat(buildImageUrl(null)).isNull();
    }

    @Test
    void buildImageUrl_blank_returnsNull() {
        assertThat(buildImageUrl("  ")).isNull();
    }

    @Test
    void buildImageUrl_partialPath_prependsTtbBase() {
        assertThat(buildImageUrl("/images/foo.jpg"))
                .isEqualTo("https://www.ttb.gov/images/foo.jpg");
    }

    @Test
    void buildImageUrl_fullHttpUrl_returnedAsIs() {
        assertThat(buildImageUrl("https://example.com/label.jpg"))
                .isEqualTo("https://example.com/label.jpg");
    }

    @Test
    void buildImageUrl_fullHttpsUrl_returnedAsIs() {
        assertThat(buildImageUrl("https://www.ttb.gov/images/label.jpg"))
                .isEqualTo("https://www.ttb.gov/images/label.jpg");
    }

    @Test
    void buildImageUrl_whitespaceStripped() {
        assertThat(buildImageUrl("  /images/label.jpg  "))
                .isEqualTo("https://www.ttb.gov/images/label.jpg");
    }

    // toEntity()

    @Test
    void toEntity_nullTtbId_returnsNull() {
        SocrataRecord sr = new SocrataRecord(null, "Brand", null, null, null, null, null, null, null, null);
        assertThat(service.toEntity(sr)).isNull();
    }

    @Test
    void toEntity_blankTtbId_returnsNull() {
        SocrataRecord sr = new SocrataRecord("  ", "Brand", null, null, null, null, null, null, null, null);
        assertThat(service.toEntity(sr)).isNull();
    }

    @Test
    void toEntity_validRecord_mapsAllFields() {
        SocrataRecord sr = new SocrataRecord(
                "12345678",
                "  BUFFALO TRACE  ",
                "Single Barrel",
                "BOURBON WHISKY",
                "Buffalo Trace Distillery",
                "2023-05-10T00:00:00.000",
                "APPROVED",
                "/images/cola/12345678.jpg",
                "SER-001",
                "DISTILLED SPIRITS"
        );
        ColaRecord r = service.toEntity(sr);

        assertThat(r).isNotNull();
        assertThat(r.getTtbId()).isEqualTo("12345678");
        assertThat(r.getBrandName()).isEqualTo("BUFFALO TRACE");
        assertThat(r.getFancifulName()).isEqualTo("Single Barrel");
        assertThat(r.getClassType()).isEqualTo("BOURBON WHISKY");
        assertThat(r.getApplicantName()).isEqualTo("Buffalo Trace Distillery");
        assertThat(r.getApprovalDate()).isEqualTo(LocalDate.of(2023, 5, 10));
        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(r.getLabelImageUrl()).isEqualTo("https://www.ttb.gov/images/cola/12345678.jpg");
        assertThat(r.getSeriesId()).isEqualTo("SER-001");
        assertThat(r.getBeverageType()).isEqualTo("DISTILLED SPIRITS");
        assertThat(r.getRawJson()).contains("12345678");
    }

    @Test
    void toEntity_naFieldsTreatedAsNull() {
        SocrataRecord sr = new SocrataRecord("99999", "N/A", "n/a", null, null, null, "APPROVED", null, null, null);
        ColaRecord r = service.toEntity(sr);

        assertThat(r).isNotNull();
        assertThat(r.getBrandName()).isNull();
        assertThat(r.getFancifulName()).isNull();
    }
}

package gov.treas.ttb.cola.viewer.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import gov.treas.ttb.cola.viewer.ColasLabelViewerApplication;
import gov.treas.ttb.cola.viewer.model.ColaRecord;
import gov.treas.ttb.cola.viewer.service.ColaIngestionService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ColasLabelViewerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class TtbOnlineClientIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    static WireMockServer wireMock;

    static final String SAMPLE_CSV = """
            TTB ID,Permit No.,Serial Number,Completed Date,Fanciful Name,Brand Name,Origin,Origin Desc,Class/Type,Class/Type Desc
            '24009001000244,DSP-KY-113,240013,02/09/2024,SBS,BUFFALO TRACE,22,KENTUCKY,101,STRAIGHT BOURBON WHISKY
            '03211001000018,BW-MI-159,030035,12/18/2024,CASCADE VAL,CASCADE WINERY,06,MICHIGAN,80,TABLE RED WINE
            """;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url", postgres::getJdbcUrl);
        reg.add("spring.datasource.username", postgres::getUsername);
        reg.add("spring.datasource.password", postgres::getPassword);
        reg.add("ttb.online.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @Autowired
    ColaIngestionService ingestionService;

    @Test
    void fetchCsvChunk_validResponse_returnsRecords() {
        stubFor(get(urlPathEqualTo("/colasonline/publicSearchColasBasic.do"))
                .willReturn(ok().withBody("<html>search form</html>")));
        stubFor(post(urlPathEqualTo("/colasonline/publicSearchColasBasicProcess.do"))
                .willReturn(ok().withBody("<html>results</html>")));
        stubFor(get(urlPathEqualTo("/colasonline/publicSaveSearchResultsToFile.do"))
                .willReturn(ok()
                        .withHeader("Content-Type", "text/csv")
                        .withBody(SAMPLE_CSV)));

        String csv = ingestionService.fetchCsvChunk(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31));

        assertThat(csv).contains("BUFFALO TRACE");
        List<ColaRecord> records = ingestionService.parseCsv(csv);
        assertThat(records).hasSize(2);
        assertThat(records.get(0).getTtbId()).isEqualTo("24009001000244");
        assertThat(records.get(0).getBrandName()).isEqualTo("BUFFALO TRACE");
        assertThat(records.get(1).getBeverageType()).isEqualTo("WINE");
    }

    @Test
    void fetchCsvChunk_emptyResults_returnsEmptyList() {
        stubFor(get(urlPathEqualTo("/colasonline/publicSearchColasBasic.do"))
                .willReturn(ok().withBody("<html></html>")));
        stubFor(post(urlPathEqualTo("/colasonline/publicSearchColasBasicProcess.do"))
                .willReturn(ok().withBody("<html></html>")));
        stubFor(get(urlPathEqualTo("/colasonline/publicSaveSearchResultsToFile.do"))
                .willReturn(ok()
                        .withHeader("Content-Type", "text/csv")
                        .withBody("TTB ID,Permit No.,Serial Number,Completed Date,Fanciful Name,Brand Name,Origin,Origin Desc,Class/Type,Class/Type Desc\n")));

        String csv = ingestionService.fetchCsvChunk(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31));
        assertThat(ingestionService.parseCsv(csv)).isEmpty();
    }

    @Test
    void parseCsv_unknownExtraColumns_ignoredGracefully() {
        String csvWithExtra = """
                TTB ID,Permit No.,Serial Number,Completed Date,Fanciful Name,Brand Name,Origin,Origin Desc,Class/Type,Class/Type Desc,NEW_FUTURE_COLUMN
                '99999999999999,DSP-KY-1,000001,01/01/2024,,Test Brand,22,KENTUCKY,101,STRAIGHT BOURBON WHISKY,ignored
                """;
        List<ColaRecord> records = ingestionService.parseCsv(csvWithExtra);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getTtbId()).isEqualTo("99999999999999");
    }
}

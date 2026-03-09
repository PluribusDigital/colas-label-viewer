package gov.treas.ttb.cola.viewer.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import gov.treas.ttb.cola.viewer.ColasLabelViewerApplication;
import gov.treas.ttb.cola.viewer.model.SocrataRecord;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = ColasLabelViewerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class SocrataClientIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    static WireMockServer wireMock;

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
        reg.add("ttb.socrata.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @Autowired
    ColaIngestionService ingestionService;

    @Test
    void fetchPage_validResponse_deserializesAllFields() throws IOException {
        String fixture = new String(Objects.requireNonNull(
                getClass().getResourceAsStream("/fixtures/socrata_page.json")).readAllBytes(),
                StandardCharsets.UTF_8);

        wireMock.stubFor(get(urlPathEqualTo("/resource/n48b-hpqm.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(fixture)));

        List<SocrataRecord> page = ingestionService.fetchPage(0);

        assertThat(page).hasSize(2);
        assertThat(page.get(0).ttbId()).isEqualTo("12345678");
        assertThat(page.get(0).brandName()).isEqualTo("BUFFALO TRACE");
        assertThat(page.get(1).ttbId()).isEqualTo("87654321");
        assertThat(page.get(1).fancifulName()).isNull();
    }

    @Test
    void fetchPage_emptyArray_returnsEmptyList() {
        wireMock.stubFor(get(urlPathEqualTo("/resource/n48b-hpqm.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        List<SocrataRecord> page = ingestionService.fetchPage(0);
        assertThat(page).isEmpty();
    }

    @Test
    void fetchPage_unknownFieldsInResponse_ignoredGracefully() {
        wireMock.stubFor(get(urlPathEqualTo("/resource/n48b-hpqm.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [{"ttb_id":"99","brand_name":"Test","new_field_2025":"ignored"}]
                                """)));

        List<SocrataRecord> page = ingestionService.fetchPage(0);
        assertThat(page).hasSize(1);
        assertThat(page.get(0).ttbId()).isEqualTo("99");
    }
}

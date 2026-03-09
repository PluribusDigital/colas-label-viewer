package gov.treas.ttb.cola.viewer.integration;

import gov.treas.ttb.cola.viewer.model.ColaRecord;
import gov.treas.ttb.cola.viewer.repository.ColaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ColaRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url", postgres::getJdbcUrl);
        reg.add("spring.datasource.username", postgres::getUsername);
        reg.add("spring.datasource.password", postgres::getPassword);
        reg.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    ColaRepository repo;

    @BeforeEach
    void seed() {
        repo.deleteAll();
        repo.upsert(buildRecord("A001", "Buffalo Trace", null, "BOURBON WHISKY", "Buffalo Trace Distillery", "DISTILLED SPIRITS", "APPROVED"));
        repo.upsert(buildRecord("A002", "Silver Oak", "Napa Valley", "CABERNET SAUVIGNON", "Silver Oak Cellars", "WINE", "APPROVED"));
        repo.upsert(buildRecord("A003", "Goose Island", "IPA", "ALE", "Goose Island Beer Co", "MALT BEVERAGES", "DELETED"));
    }

    @Test
    void upsert_newRecord_persisted() {
        assertThat(repo.findByTtbId("A001")).isPresent();
        assertThat(repo.findByTtbId("A001").get().getBrandName()).isEqualTo("Buffalo Trace");
    }

    @Test
    void upsert_existingTtbId_updatesFields() {
        ColaRecord updated = buildRecord("A001", "Buffalo Trace Updated", null, "BOURBON WHISKY", "BT Distillery", "DISTILLED SPIRITS", "APPROVED");
        repo.upsert(updated);

        assertThat(repo.findByTtbId("A001").get().getBrandName()).isEqualTo("Buffalo Trace Updated");
        assertThat(repo.findByTtbId("A001").get().getApplicantName()).isEqualTo("BT Distillery");
    }

    @Test
    void searchRecords_emptyQuery_returnsAll() {
        List<ColaRecord> results = repo.searchRecords("", null, null, 10, 0);
        assertThat(results).hasSize(3);
    }

    @Test
    void searchRecords_brandName_matchesFts() {
        List<ColaRecord> results = repo.searchRecords("buffalo", null, null, 10, 0);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTtbId()).isEqualTo("A001");
    }

    @Test
    void searchRecords_partialTermMatchesMultiple() {
        List<ColaRecord> results = repo.searchRecords("oak", null, null, 10, 0);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getBrandName()).isEqualTo("Silver Oak");
    }

    @Test
    void searchRecords_beverageTypeFilter_narrowsResults() {
        List<ColaRecord> results = repo.searchRecords("", "WINE", null, 10, 0);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTtbId()).isEqualTo("A002");
    }

    @Test
    void searchRecords_statusFilter_narrowsResults() {
        List<ColaRecord> results = repo.searchRecords("", null, "DELETED", 10, 0);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTtbId()).isEqualTo("A003");
    }

    @Test
    void countRecords_matchesSearchRecordsSize() {
        long count = repo.countRecords("buffalo", null, null);
        List<ColaRecord> records = repo.searchRecords("buffalo", null, null, 10, 0);
        assertThat(count).isEqualTo(records.size());
    }

    @Test
    void findDistinctBeverageTypes_returnsSortedList() {
        List<String> types = repo.findDistinctBeverageTypes();
        assertThat(types).containsExactly("DISTILLED SPIRITS", "MALT BEVERAGES", "WINE");
    }

    private ColaRecord buildRecord(String ttbId, String brand, String fanciful,
                                   String classType, String applicant,
                                   String beverageType, String status) {
        ColaRecord r = new ColaRecord();
        r.setTtbId(ttbId);
        r.setBrandName(brand);
        r.setFancifulName(fanciful);
        r.setClassType(classType);
        r.setApplicantName(applicant);
        r.setBeverageType(beverageType);
        r.setStatus(status);
        r.setApprovalDate(LocalDate.of(2023, 6, 1));
        return r;
    }
}

package gov.treas.ttb.cola.viewer.service;

import gov.treas.ttb.cola.viewer.model.ColaDto;
import gov.treas.ttb.cola.viewer.model.ColaRecord;
import gov.treas.ttb.cola.viewer.model.PagedResponse;
import gov.treas.ttb.cola.viewer.repository.ColaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ColaSearchServiceTest {

    @Mock ColaRepository repo;
    @InjectMocks ColaSearchService service;

    @Test
    void search_emptyQuery_callsRepoWithEmptyString() {
        when(repo.searchRecords(eq(""), isNull(), isNull(), eq(24), eq(0))).thenReturn(List.of());
        when(repo.countRecords(eq(""), isNull(), isNull())).thenReturn(0L);

        service.search("", null, null, 0, 24);

        verify(repo).searchRecords("", null, null, 24, 0);
    }

    @Test
    void search_nullQuery_treatedAsEmpty() {
        when(repo.searchRecords(eq(""), isNull(), isNull(), eq(24), eq(0))).thenReturn(List.of());
        when(repo.countRecords(eq(""), isNull(), isNull())).thenReturn(0L);

        service.search(null, null, null, 0, 24);

        verify(repo).searchRecords("", null, null, 24, 0);
    }

    @Test
    void search_pagination_offsetCalculatedCorrectly() {
        when(repo.searchRecords(anyString(), isNull(), isNull(), eq(24), eq(48))).thenReturn(List.of());
        when(repo.countRecords(anyString(), isNull(), isNull())).thenReturn(0L);

        service.search("buffalo", null, null, 2, 24);

        verify(repo).searchRecords("buffalo", null, null, 24, 48);
    }

    @Test
    void search_resultsMappedToDtos() {
        ColaRecord record = buildRecord("TTB-001", "Buffalo Trace");
        when(repo.searchRecords(anyString(), any(), any(), anyInt(), anyInt())).thenReturn(List.of(record));
        when(repo.countRecords(anyString(), any(), any())).thenReturn(1L);

        PagedResponse<ColaDto> result = service.search("buffalo", null, null, 0, 24);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).ttbId()).isEqualTo("TTB-001");
        assertThat(result.content().get(0).brandName()).isEqualTo("Buffalo Trace");
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void search_labelImageUrl_isProxyPath() {
        ColaRecord record = buildRecord("TTB-001", "Brand");
        when(repo.searchRecords(anyString(), any(), any(), anyInt(), anyInt())).thenReturn(List.of(record));
        when(repo.countRecords(anyString(), any(), any())).thenReturn(1L);

        PagedResponse<ColaDto> result = service.search("", null, null, 0, 24);

        assertThat(result.content().get(0).labelImageUrl()).isEqualTo("/api/image/TTB-001");
    }

    @Test
    void findByTtbId_notFound_returnsEmpty() {
        when(repo.findByTtbId("MISSING")).thenReturn(Optional.empty());
        assertThat(service.findByTtbId("MISSING")).isEmpty();
    }

    @Test
    void findByTtbId_found_returnsDtoWithProxyUrl() {
        ColaRecord record = buildRecord("TTB-999", "My Brand");
        when(repo.findByTtbId("TTB-999")).thenReturn(Optional.of(record));

        Optional<ColaDto> result = service.findByTtbId("TTB-999");

        assertThat(result).isPresent();
        assertThat(result.get().ttbId()).isEqualTo("TTB-999");
        assertThat(result.get().labelImageUrl()).isEqualTo("/api/image/TTB-999");
    }

    private ColaRecord buildRecord(String ttbId, String brandName) {
        ColaRecord r = new ColaRecord();
        r.setTtbId(ttbId);
        r.setBrandName(brandName);
        r.setApprovalDate(LocalDate.of(2023, 1, 1));
        r.setStatus("APPROVED");
        return r;
    }
}

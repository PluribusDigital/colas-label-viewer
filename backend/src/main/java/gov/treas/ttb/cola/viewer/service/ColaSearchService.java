package gov.treas.ttb.cola.viewer.service;

import gov.treas.ttb.cola.viewer.model.ColaDto;
import gov.treas.ttb.cola.viewer.model.PagedResponse;
import gov.treas.ttb.cola.viewer.repository.ColaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ColaSearchService {

    private final ColaRepository colaRepository;

    public PagedResponse<ColaDto> search(String q, String beverageType, String status, int page, int size) {
        String query = q == null ? "" : q.strip();
        int offset = page * size;

        List<ColaDto> content = colaRepository
                .searchRecords(query, beverageType, status, size, offset)
                .stream()
                .map(ColaDto::from)
                .toList();

        long total = colaRepository.countRecords(query, beverageType, status);
        int totalPages = (int) Math.ceil((double) total / size);

        return new PagedResponse<>(content, total, totalPages, page, size, page < totalPages - 1);
    }

    public Optional<ColaDto> findByTtbId(String ttbId) {
        return colaRepository.findByTtbId(ttbId).map(ColaDto::from);
    }

    public List<String> getBeverageTypes() {
        return colaRepository.findDistinctBeverageTypes();
    }
}

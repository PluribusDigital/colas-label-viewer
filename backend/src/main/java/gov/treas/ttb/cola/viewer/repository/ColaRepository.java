package gov.treas.ttb.cola.viewer.repository;

import gov.treas.ttb.cola.viewer.model.ColaRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ColaRepository extends JpaRepository<ColaRecord, Long> {

    Optional<ColaRecord> findByTtbId(String ttbId);

    @Query(value = """
            SELECT * FROM cola_records
            WHERE (:q = '' OR search_vector @@ plainto_tsquery('english', :q))
              AND (:beverageType IS NULL OR beverage_type = :beverageType)
              AND (:status IS NULL OR status = :status)
            ORDER BY
              CASE WHEN :q = '' THEN 0
                   ELSE ts_rank_cd(search_vector, plainto_tsquery('english', :q)) END DESC,
              approval_date DESC NULLS LAST
            LIMIT :size OFFSET :offset
            """,
            nativeQuery = true)
    List<ColaRecord> searchRecords(
            @Param("q") String q,
            @Param("beverageType") String beverageType,
            @Param("status") String status,
            @Param("size") int size,
            @Param("offset") int offset
    );

    @Query(value = """
            SELECT COUNT(*) FROM cola_records
            WHERE (:q = '' OR search_vector @@ plainto_tsquery('english', :q))
              AND (:beverageType IS NULL OR beverage_type = :beverageType)
              AND (:status IS NULL OR status = :status)
            """,
            nativeQuery = true)
    long countRecords(
            @Param("q") String q,
            @Param("beverageType") String beverageType,
            @Param("status") String status
    );

    @Query(value = "SELECT DISTINCT beverage_type FROM cola_records WHERE beverage_type IS NOT NULL ORDER BY beverage_type",
            nativeQuery = true)
    List<String> findDistinctBeverageTypes();

    @Modifying
    @Query(value = """
            INSERT INTO cola_records
              (ttb_id, brand_name, fanciful_name, class_type, applicant_name,
               approval_date, status, label_image_url, series_id, beverage_type, raw_json,
               created_at, updated_at)
            VALUES
              (:#{#r.ttbId}, :#{#r.brandName}, :#{#r.fancifulName}, :#{#r.classType},
               :#{#r.applicantName}, :#{#r.approvalDate}, :#{#r.status}, :#{#r.labelImageUrl},
               :#{#r.seriesId}, :#{#r.beverageType}, :#{#r.rawJson},
               now(), now())
            ON CONFLICT (ttb_id) DO UPDATE SET
              brand_name      = EXCLUDED.brand_name,
              fanciful_name   = EXCLUDED.fanciful_name,
              class_type      = EXCLUDED.class_type,
              applicant_name  = EXCLUDED.applicant_name,
              approval_date   = EXCLUDED.approval_date,
              status          = EXCLUDED.status,
              label_image_url = EXCLUDED.label_image_url,
              series_id       = EXCLUDED.series_id,
              beverage_type   = EXCLUDED.beverage_type,
              raw_json        = EXCLUDED.raw_json,
              updated_at      = now()
            """,
            nativeQuery = true)
    void upsert(@Param("r") ColaRecord r);
}

package gov.treas.ttb.cola.viewer.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "cola_records")
@Getter
@Setter
@NoArgsConstructor
public class ColaRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ttb_id", nullable = false, unique = true, length = 50)
    private String ttbId;

    @Column(name = "brand_name", length = 500)
    private String brandName;

    @Column(name = "fanciful_name", length = 500)
    private String fancifulName;

    @Column(name = "class_type", length = 300)
    private String classType;

    @Column(name = "applicant_name", length = 500)
    private String applicantName;

    @Column(name = "approval_date")
    private LocalDate approvalDate;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "label_image_url")
    private String labelImageUrl;

    @Column(name = "series_id", length = 50)
    private String seriesId;

    @Column(name = "beverage_type", length = 100)
    private String beverageType;

    @Column(name = "raw_json", columnDefinition = "text")
    private String rawJson;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

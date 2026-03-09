package gov.treas.ttb.cola.viewer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SocrataRecord(
        @JsonProperty("ttb_id")                String ttbId,
        @JsonProperty("brand_name")             String brandName,
        @JsonProperty("fanciful_name")          String fancifulName,
        @JsonProperty("class_type_description") String classTypeDescription,
        @JsonProperty("applicant_name")         String applicantName,
        @JsonProperty("approval_date")          String approvalDate,
        @JsonProperty("status")                 String status,
        @JsonProperty("label_image_file")       String labelImageFile,
        @JsonProperty("series_id")              String seriesId,
        @JsonProperty("beverage_type")          String beverageType
) {}

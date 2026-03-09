package gov.treas.ttb.cola.viewer.model;

public record ColaDto(
        String ttbId,
        String brandName,
        String fancifulName,
        String classType,
        String applicantName,
        String approvalDate,
        String status,
        String labelImageUrl,
        String seriesId,
        String beverageType
) {
    public static ColaDto from(ColaRecord r) {
        return new ColaDto(
                r.getTtbId(),
                r.getBrandName(),
                r.getFancifulName(),
                r.getClassType(),
                r.getApplicantName(),
                r.getApprovalDate() != null ? r.getApprovalDate().toString() : null,
                r.getStatus(),
                "/api/image/" + r.getTtbId(),
                r.getSeriesId(),
                r.getBeverageType()
        );
    }
}

package com.example.demo.summary.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * The versioned summary at {@code /api/v1/summary}.
 *
 * <p>A separate type from {@link SummaryResponse} on purpose. The unversioned endpoint is what
 * the guiding page reads in production, and it keeps its exact body through the migration; adding
 * the identity and the version marker to that class would have changed it for every existing
 * reader on the day this shipped. The two are built from one snapshot, so they cannot disagree
 * about the numbers.
 *
 * <p>Schema: contracts/v1/schemas/summary.schema.json.
 */
@JsonPropertyOrder({"contract", "version", "schoolId", "slug", "schoolName", "shortName", "address",
    "status", "clubCount", "categories", "memberCount", "lastUpdatedAt", "dataHash"})
public class SummaryV1Response {

    private static final String CONTRACT = "hsclubs.summary";
    private static final int VERSION = 1;

    private final String schoolId;
    private final SummaryResponse summary;

    public SummaryV1Response(String schoolId, SummaryResponse summary) {
        this.schoolId = schoolId;
        this.summary = summary;
    }

    public String getContract() { return CONTRACT; }

    public int getVersion() { return VERSION; }

    public String getSchoolId() { return schoolId; }

    public String getSlug() { return summary.getSlug(); }

    public String getSchoolName() { return summary.getSchoolName(); }

    public String getShortName() { return summary.getShortName(); }

    public String getAddress() { return summary.getAddress(); }

    public String getStatus() { return summary.getStatus(); }

    public int getClubCount() { return summary.getClubCount(); }

    public Map<String, Integer> getCategories() { return summary.getCategories(); }

    public int getMemberCount() { return summary.getMemberCount(); }

    public OffsetDateTime getLastUpdatedAt() { return summary.getLastUpdatedAt(); }

    public String getDataHash() { return summary.getDataHash(); }
}

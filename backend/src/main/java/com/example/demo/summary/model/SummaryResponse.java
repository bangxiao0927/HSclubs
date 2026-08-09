package com.example.demo.summary.model;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Public summary of this school's club directory.
 * Consumed by the 2nd-repo aggregator to display platform-wide stats.
 */
public class SummaryResponse {

    private String schoolName;
    private String shortName;
    private String slug;
    private String status;
    private int clubCount;
    private Map<String, Integer> categories;
    private int memberCount;
    /**
     * When this school's directory last changed, as an ISO-8601 instant with an offset.
     *
     * <p>Offset-bearing on purpose: this field leaves the building. A wall-clock time with no
     * zone is only interpretable by someone who knows which zone this server keeps, and the
     * consumer is a page comparing schools that need not share one.
     */
    private OffsetDateTime lastUpdatedAt;
    private String dataHash;

    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }

    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getClubCount() { return clubCount; }
    public void setClubCount(int clubCount) { this.clubCount = clubCount; }

    public Map<String, Integer> getCategories() { return categories; }
    public void setCategories(Map<String, Integer> categories) { this.categories = categories; }

    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }

    public OffsetDateTime getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(OffsetDateTime lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }

    public String getDataHash() { return dataHash; }
    public void setDataHash(String dataHash) { this.dataHash = dataHash; }
}

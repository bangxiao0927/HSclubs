package com.example.demo.summary.model;

import java.time.LocalDateTime;
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
    private LocalDateTime lastUpdatedAt;
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

    public LocalDateTime getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }

    public String getDataHash() { return dataHash; }
    public void setDataHash(String dataHash) { this.dataHash = dataHash; }
}

package com.example.demo.summary.model;

import java.time.LocalDateTime;

/**
 * The narrow slice of a club row that {@code /api/summary} needs: enough to count clubs and
 * members, group by category, find the newest update, and fingerprint the directory.
 *
 * <p>Exists so that endpoint stops selecting whole {@code Club} rows -- description,
 * {@code schedule_note}, and the {@code achievements} CLOB -- for every club on every request.
 */
public class ClubSummaryProjection {

    private Long id;
    private String name;
    private String category;
    private Integer memberCount;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

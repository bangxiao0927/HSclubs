package com.example.demo.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaginationClampsTest {

    @Test
    void clampPageSizeTreatsNonPositiveSizeAsOne() {
        assertThat(PaginationClamps.clampPageSize(-100)).isEqualTo(1);
        assertThat(PaginationClamps.clampPageSize(0)).isEqualTo(1);
    }

    @Test
    void clampPageSizeCapsAtOneHundred() {
        assertThat(PaginationClamps.clampPageSize(10_000)).isEqualTo(100);
    }

    @Test
    void clampPageSizePassesThroughValuesInRange() {
        assertThat(PaginationClamps.clampPageSize(37)).isEqualTo(37);
    }

    @Test
    void clampOffsetTreatsNegativePageAsFirstPage() {
        assertThat(PaginationClamps.clampOffset(-5, 10)).isEqualTo(0);
    }

    @Test
    void clampOffsetMultipliesPageByLimit() {
        assertThat(PaginationClamps.clampOffset(2, 10)).isEqualTo(20);
    }

    @Test
    void clampOffsetNeverOverflowsForHugePageNumbers() {
        assertThat(PaginationClamps.clampOffset(Integer.MAX_VALUE, 100)).isEqualTo(Integer.MAX_VALUE);
    }
}

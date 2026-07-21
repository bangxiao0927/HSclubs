package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClubLocationNormalizerTest {

    @Test
    void prefixesNumericAndBuildingRoomNumbers() {
        assertThat(ClubLocationNormalizer.normalize("604")).isEqualTo("Room 604");
        assertThat(ClubLocationNormalizer.normalize("A212")).isEqualTo("Room A212");
        assertThat(ClubLocationNormalizer.normalize("P8")).isEqualTo("Room P8");
        assertThat(ClubLocationNormalizer.normalize("117 or 730")).isEqualTo("Room 117 or 730");
    }

    @Test
    void canonicalizesExistingRoomPrefix() {
        assertThat(ClubLocationNormalizer.normalize("room 112")).isEqualTo("Room 112");
        assertThat(ClubLocationNormalizer.normalize("Room 121")).isEqualTo("Room 121");
    }

    @Test
    void preservesNamedLocations() {
        assertThat(ClubLocationNormalizer.normalize("Library, and zoom"))
            .isEqualTo("Library, and zoom");
        assertThat(ClubLocationNormalizer.normalize("Packard Hall")).isEqualTo("Packard Hall");
        assertThat(ClubLocationNormalizer.normalize(null)).isNull();
    }
}

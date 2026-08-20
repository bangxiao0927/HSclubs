package com.example.demo.summary.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Configuration read once at startup, where a mistake is cheap to fix and expensive to publish.
 */
class SchoolIdentityTest {

    private static final String ID = "sch_7Qb3Xf9KLm2ZpR4tVn6Y";

    @Test
    void treatsAnUnconfiguredDeploymentAsNotHavingJoinedV1() {
        SchoolIdentity identity = new SchoolIdentity("", "");

        assertThat(identity.schoolId()).isEmpty();
        assertThat(identity.publishesV1()).isFalse();
    }

    // A truncated or mistyped value is not a smaller problem than an empty one: it makes this
    // origin claim to be a school it is not, which the registry reads as a stolen identity.
    @Test
    void refusesAnIdentityThatIsNotOne() {
        for (String wrong : new String[] {"mvhs", "sch_", "sch_short", "sch_has-a-hyphen-inside-it", " sch"}) {
            assertThatThrownBy(() -> new SchoolIdentity(wrong, "https://clubs.example.edu"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.school.id");
        }
    }

    @Test
    void acceptsTheIssuedIdentityAndToleratesSurroundingWhitespace() {
        assertThat(new SchoolIdentity("  " + ID + "  ", "https://clubs.example.edu").schoolId())
            .contains(ID);
    }

    @Test
    void reducesTheConfiguredSiteAddressToAnOrigin() {
        assertThat(new SchoolIdentity(ID, "https://clubs.example.edu/").siteOrigin())
            .contains("https://clubs.example.edu");
        assertThat(new SchoolIdentity(ID, "https://clubs.example.edu:8443/some/path").siteOrigin())
            .contains("https://clubs.example.edu:8443");
    }

    @Test
    void refusesAnOriginThatIsNotHttps() {
        assertThatThrownBy(() -> new SchoolIdentity(ID, "http://clubs.example.edu"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("app.school.site-origin");
    }

    // Both halves or nothing: an identity with no origin cannot produce a manifest the registry
    // can check against the host it verified.
    @Test
    void publishesV1OnlyWhenItKnowsBothItsIdentityAndItsOrigin() {
        assertThat(new SchoolIdentity(ID, "").publishesV1()).isFalse();
        assertThat(new SchoolIdentity("", "https://clubs.example.edu").publishesV1()).isFalse();
        assertThat(new SchoolIdentity(ID, "https://clubs.example.edu").publishesV1()).isTrue();
    }
}

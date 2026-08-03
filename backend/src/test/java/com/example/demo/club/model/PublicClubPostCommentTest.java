package com.example.demo.club.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Regression guard for the DTO's own contract, independent of the mapper: same rule as
 * {@link PublicClubPostTest}, since #79 reuses the no-PII author projection for comments.
 */
class PublicClubPostCommentTest {

    private static final String[] FORBIDDEN_SUBSTRINGS = {
        "email", "uid", "provider", "role", "acceptedterms", "graduationyear", "lastlogin"
    };

    @Test
    void hasNoFieldOrAccessorNamedAfterAForbiddenOauthUserColumn() {
        Stream.concat(
                Stream.of(PublicClubPostComment.class.getDeclaredFields()).map(Field::getName),
                Stream.of(PublicClubPostComment.class.getDeclaredMethods()).map(Method::getName))
            .forEach(name -> {
                String normalized = name.toLowerCase(Locale.ROOT).replace("_", "");
                for (String forbidden : FORBIDDEN_SUBSTRINGS) {
                    assertThat(normalized)
                        .withFailMessage("PublicClubPostComment member '%s' looks like it exposes "
                            + "the forbidden oauth_users column pattern '%s'", name, forbidden)
                        .doesNotContain(forbidden);
                }
            });
    }
}

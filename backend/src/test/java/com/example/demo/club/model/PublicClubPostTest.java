package com.example.demo.club.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Regression guard for the DTO's own contract, independent of the mapper: nothing on this class
 * should ever be named after an oauth_users column that could identify or contact the author
 * (email, uid, provider, provider_user_id, role, accepted_terms_at, the user's created_at/
 * last_login_at, or graduation_year). A field or getter named after one of those would leak it
 * to anonymous callers the moment someone wired it up in ClubPostMapper.xml, without this test
 * failing any differently than adding a normal field.
 */
class PublicClubPostTest {

    private static final String[] FORBIDDEN_SUBSTRINGS = {
        "email", "uid", "provider", "role", "acceptedterms", "graduationyear", "lastlogin"
    };

    @Test
    void hasNoFieldOrAccessorNamedAfterAForbiddenOauthUserColumn() {
        Stream.concat(
                Stream.of(PublicClubPost.class.getDeclaredFields()).map(Field::getName),
                Stream.of(PublicClubPost.class.getDeclaredMethods()).map(Method::getName))
            .forEach(name -> {
                String normalized = name.toLowerCase(Locale.ROOT).replace("_", "");
                for (String forbidden : FORBIDDEN_SUBSTRINGS) {
                    assertThat(normalized)
                        .withFailMessage("PublicClubPost member '%s' looks like it exposes the "
                            + "forbidden oauth_users column pattern '%s'", name, forbidden)
                        .doesNotContain(forbidden);
                }
            });
    }
}

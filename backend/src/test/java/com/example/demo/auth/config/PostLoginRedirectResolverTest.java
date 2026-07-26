package com.example.demo.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * Plain unit tests for {@link PostLoginRedirectResolver} — no Spring context
 * needed, since the class under test has no framework dependencies beyond a
 * couple of stateless Spring Web helper classes.
 *
 * <p>Note: {@code UriUtils.encodeQueryParam} leaves {@code /} and {@code ?}
 * un-encoded (they carry no special meaning once already inside a URI's
 * query component) but does encode {@code = & # %} and whitespace (which
 * would otherwise be parsed as delimiters, or corrupt a later decode pass).
 * The literal expected strings below were captured from the real output of
 * {@link PostLoginRedirectResolver#buildRedirectUri}, not hand-derived.
 */
class PostLoginRedirectResolverTest {

    private static final String CALLBACK = "https://frontend.example.com/auth/callback";

    @Test
    void appendsAValidInAppTargetEncodedExactlyOnce() {
        String result = PostLoginRedirectResolver.buildRedirectUri(CALLBACK, "/clubs/9?tab=events");

        assertThat(result).isEqualTo(
            "https://frontend.example.com/auth/callback?redirect=/clubs/9?tab%3Devents");
        assertThat(decodeRedirectParam(result)).isEqualTo("/clubs/9?tab=events");
    }

    @Test
    void queryStringAndFragmentSurviveTheRoundTripIntact() {
        String target = "/search?q=chess&sort=name#results";

        String result = PostLoginRedirectResolver.buildRedirectUri(CALLBACK, target);

        assertThat(decodeRedirectParam(result)).isEqualTo(target);
    }

    @Test
    void aLiteralPercentSignInTheTargetIsNotDoubleEncodedOrDoubleDecoded() {
        // If encoding ran twice, "%" (encoded once to "%25") would become
        // "%2525"; if decoding happened anywhere it shouldn't, "%25" would
        // decode back to "%" before the browser/frontend ever sees it. The
        // round trip must land on exactly the original string.
        String target = "/search?q=50%off";

        String result = PostLoginRedirectResolver.buildRedirectUri(CALLBACK, target);

        assertThat(result).contains("q%3D50%25off");
        assertThat(decodeRedirectParam(result)).isEqualTo(target);
    }

    @Test
    void aLiteralPlusInAQueryStringSurvivesTheRoundTripInsteadOfBecomingASpace() {
        // vue-router (like most application/x-www-form-urlencoded query
        // parsers) decodes an unescaped '+' in a query string as a space.
        // UriUtils.encodeQueryParam leaves '+' alone since it is a legal
        // RFC 3986 query character, so it must be escaped to "%2B" here or
        // a search for "C++" would arrive at the frontend as "C  ".
        String target = "/search?q=C++";

        String result = PostLoginRedirectResolver.buildRedirectUri(CALLBACK, target);

        assertThat(result).contains("q%3DC%2B%2B");
        assertThat(result).doesNotContain("q%3DC++");
        assertThat(decodeRedirectParam(result)).isEqualTo(target);
    }

    @Test
    void aLiteralPlusInABarePathSurvivesTheRoundTrip() {
        String target = "/clubs/a+b";

        String result = PostLoginRedirectResolver.buildRedirectUri(CALLBACK, target);

        assertThat(result).contains("/clubs/a%2Bb");
        assertThat(decodeRedirectParam(result)).isEqualTo(target);
    }

    @Test
    void ampersandEqualsHashAndPercentStillRoundTripAfterThePlusFix() {
        // Re-asserts an existing payload shape covered above, guarding
        // against a regression in the encoder introduced while fixing the
        // '+' handling.
        String target = "/search?q=50%off&sort=name#results";

        String result = PostLoginRedirectResolver.buildRedirectUri(CALLBACK, target);

        assertThat(decodeRedirectParam(result)).isEqualTo(target);
    }

    @Test
    void noTargetYieldsTheBareCallbackUrl() {
        assertThat(PostLoginRedirectResolver.buildRedirectUri(CALLBACK, null)).isEqualTo(CALLBACK);
        assertThat(PostLoginRedirectResolver.buildRedirectUri(CALLBACK, "")).isEqualTo(CALLBACK);
        assertThat(PostLoginRedirectResolver.buildRedirectUri(CALLBACK, "   ")).isEqualTo(CALLBACK);
    }

    @Test
    void rejectsProtocolRelativeDoubleSlash() {
        assertThat(PostLoginRedirectResolver.buildRedirectUri(CALLBACK, "//evil.com")).isEqualTo(CALLBACK);
    }

    @Test
    void rejectsBackslashProtocolRelativeVariant() {
        assertThat(PostLoginRedirectResolver.buildRedirectUri(CALLBACK, "/\\evil.com")).isEqualTo(CALLBACK);
    }

    @Test
    void rejectsAbsoluteUrl() {
        assertThat(PostLoginRedirectResolver.buildRedirectUri(CALLBACK, "https://evil.com")).isEqualTo(CALLBACK);
    }

    @Test
    void rejectsSchemeBearingValueWithoutLeadingSlash() {
        assertThat(PostLoginRedirectResolver.buildRedirectUri(CALLBACK, "javascript:alert(1)")).isEqualTo(CALLBACK);
    }

    @Test
    void rejectsEmbeddedSchemeEvenWithLeadingSlash() {
        assertThat(PostLoginRedirectResolver.buildRedirectUri(CALLBACK, "/redirect?to=https://evil.com"))
            .isEqualTo(CALLBACK);
    }

    @Test
    void rejectsCarriageReturnLineFeedInjection() {
        assertThat(PostLoginRedirectResolver.buildRedirectUri(CALLBACK, "/profile\r\nSet-Cookie:evil=1"))
            .isEqualTo(CALLBACK);
    }

    @Test
    void rejectsTabCharacter() {
        assertThat(PostLoginRedirectResolver.buildRedirectUri(CALLBACK, "/pro\tfile")).isEqualTo(CALLBACK);
    }

    @Test
    void rejectsOverLengthValues() {
        String tooLong = "/" + "a".repeat(PostLoginRedirectResolver.MAX_REDIRECT_LENGTH);
        assertThat(PostLoginRedirectResolver.buildRedirectUri(CALLBACK, tooLong)).isEqualTo(CALLBACK);
    }

    @Test
    void rejectsNonSlashPrefixedValue() {
        assertThat(PostLoginRedirectResolver.buildRedirectUri(CALLBACK, "profile")).isEqualTo(CALLBACK);
    }

    @Test
    void appendsErrorParamWithoutARedirectTarget() {
        String result = PostLoginRedirectResolver.buildRedirectUri(CALLBACK, null, "oauth2_login_failed");

        assertThat(result).isEqualTo(
            "https://frontend.example.com/auth/callback?error=oauth2_login_failed");
    }

    @Test
    void appendsBothRedirectAndErrorParamsOnFailureWithARememberedTarget() {
        String result = PostLoginRedirectResolver.buildRedirectUri(CALLBACK, "/clubs/9", "oauth2_login_failed");

        assertThat(result).isEqualTo(
            "https://frontend.example.com/auth/callback?redirect=/clubs/9&error=oauth2_login_failed");
    }

    @Test
    void dropsAnInvalidTargetButStillAppendsTheErrorParam() {
        String result = PostLoginRedirectResolver.buildRedirectUri(CALLBACK, "//evil.com", "oauth2_login_failed");

        assertThat(result).isEqualTo(
            "https://frontend.example.com/auth/callback?error=oauth2_login_failed");
    }

    @Test
    void aLiteralSpacePremiseForThePlusEscapeHoldsSpaceStaysPercentTwentyNotPlus() {
        // Pins the unstated premise behind encodeQueryParamForFrontendRouter's
        // ".replace("+", "%2B")" pass: UriUtils.encodeQueryParam must encode
        // a literal space as "%20", never emit a bare '+' on its own. If that
        // ever stopped being true, the replace() above would rewrite an
        // encoder-emitted '+' into "%2B", silently turning every space in a
        // redirect target into a literal '+'. Asserting "%20" (not "+") is
        // present, and that the round trip preserves the space intact, is
        // what would catch that regression.
        String target = "/search?q=chess club";

        String result = PostLoginRedirectResolver.buildRedirectUri(CALLBACK, target);

        assertThat(result).contains("%20");
        assertThat(result).doesNotContain("+");
        assertThat(decodeRedirectParam(result)).isEqualTo(target);
    }

    @Test
    void aLiteralSpaceAndPlusTogetherDoNotGetConflatedByThePlusEscape() {
        // Same premise as above, but with a space and a literal '+' in the
        // same value: this is the case that would actually break if
        // encodeQueryParam's space and '+' encodings were ever conflated
        // (e.g. a switch to form-style encoding where a space also becomes
        // '+'). Both characters must survive the round trip distinctly.
        String target = "/search?q=C++ tutorial";

        String result = PostLoginRedirectResolver.buildRedirectUri(CALLBACK, target);

        assertThat(result).contains("q%3DC%2B%2B%20tutorial");
        assertThat(decodeRedirectParam(result)).isEqualTo(target);
    }

    private static String decodeRedirectParam(String builtUri) {
        String encoded = UriComponentsBuilder.fromUriString(builtUri)
            .build()
            .getQueryParams()
            .getFirst("redirect");
        return UriUtils.decode(encoded, StandardCharsets.UTF_8);
    }
}

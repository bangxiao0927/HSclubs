package com.example.demo.summary.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * This deployment's permanent identity, issued by the guiding page registry.
 *
 * <p>The slug is a handle: it is in URLs, somebody chose it, and a school that renames itself
 * expects it to change. The identity cannot work that way -- the app remembers which school a
 * person picked, and the aggregator keys its store on it -- so it is issued once, elsewhere, and
 * this deployment only publishes what it was given.
 *
 * <p>Empty is a supported state and the default: a school that has not joined the v1 contract yet
 * keeps serving {@code /api/summary} exactly as before and simply does not publish the v1
 * surface. A value that is present but malformed is refused at startup instead, because it can
 * only mean a mistyped or truncated configuration, and publishing a wrong identity is worse than
 * publishing none: the aggregator would read it as this origin claiming to be another school.
 *
 * <p>Format and rules: contracts/v1/README.md.
 */
@Component
public class SchoolIdentity {

    private static final Pattern SCHOOL_ID = Pattern.compile("^sch_[A-Za-z0-9]{16,48}$");
    private static final Logger log = LoggerFactory.getLogger(SchoolIdentity.class);

    private final String schoolId;
    private final String siteOrigin;

    public SchoolIdentity(@Value("${app.school.id:}") String schoolId,
                          @Value("${app.school.site-origin:}") String siteOrigin,
                          @Value("${app.security.frontend-origin:}") String frontendOrigin) {
        this.schoolId = normalizeSchoolId(schoolId);
        this.siteOrigin = normalizeOrigin(siteOrigin, frontendOrigin);
    }

    /** For tests and callers that configure the origin directly. */
    public SchoolIdentity(String schoolId, String siteOrigin) {
        this(schoolId, siteOrigin, "");
    }

    private static String normalizeSchoolId(String configured) {
        if (!StringUtils.hasText(configured)) {
            return null;
        }
        String trimmed = configured.trim();
        if (!SCHOOL_ID.matcher(trimmed).matches()) {
            throw new IllegalStateException(
                "app.school.id must be the identity issued by the guiding page registry "
                    + "(sch_ followed by 16-48 letters or digits), got: " + trimmed);
        }
        return trimmed;
    }

    /**
     * Scheme, host and port only. A manifest carrying a path would not match the origin the
     * registry verified, and the aggregator would reject the school rather than guess what was
     * meant.
     */
    private static String normalizeOrigin(String configured, String frontendOrigin) {
        if (!StringUtils.hasText(configured)) {
            return null;
        }
        // site-origin defaults to the frontend origin, which on a development machine is
        // http://localhost:5173. Refusing to start over an inherited value would make a dev
        // deployment unbootable for a surface it never asked to publish, so an unusable *default*
        // means "no v1 surface" -- the supported empty state -- while an explicitly configured
        // value that cannot work is still a startup failure.
        boolean inherited = configured.trim().equals(frontendOrigin == null ? "" : frontendOrigin.trim());
        URI uri;
        try {
            uri = new URI(configured.trim());
        } catch (URISyntaxException error) {
            if (inherited) {
                log.warn("app.school.site-origin defaulted to a frontend origin that is not a URL"
                    + " ({}); publishing no v1 surface. Set APP_SCHOOL_SITE_ORIGIN to publish one.",
                    configured);
                return null;
            }
            throw new IllegalStateException("app.school.site-origin is not a URL: " + configured, error);
        }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null) {
            if (inherited) {
                log.warn("app.school.site-origin defaulted to a non-https frontend origin ({});"
                    + " publishing no v1 surface. Set APP_SCHOOL_SITE_ORIGIN to publish one.",
                    configured);
                return null;
            }
            throw new IllegalStateException(
                "app.school.site-origin must be an https origin, got: " + configured);
        }
        return uri.getPort() < 0
            ? "https://" + uri.getHost()
            : "https://" + uri.getHost() + ":" + uri.getPort();
    }

    public Optional<String> schoolId() {
        return Optional.ofNullable(schoolId);
    }

    public Optional<String> siteOrigin() {
        return Optional.ofNullable(siteOrigin);
    }

    /**
     * Whether this deployment may publish the v1 surface at all.
     *
     * <p>Both halves are required: an identity with no origin cannot produce a manifest the
     * registry can check, and an origin with no identity is the school that has not joined yet.
     */
    public boolean publishesV1() {
        return schoolId != null && siteOrigin != null;
    }
}

package com.example.demo.summary.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * The body of {@code /.well-known/hsclubs-app.json}.
 *
 * <p>Capabilities list what this deployment actually implements today, not what the contract
 * describes: mobile authentication is declared unsupported until the endpoints behind it exist,
 * because an app that believed this manifest would otherwise send a person into a sign-in that
 * cannot complete.
 *
 * <p>Schema: contracts/v1/schemas/school-manifest.schema.json.
 */
@JsonPropertyOrder({"contract", "version", "schoolId", "slug", "schoolName", "siteOrigin",
    "summaryUrl", "capabilities", "auth"})
public class SchoolManifestResponse {

    private static final String CONTRACT = "hsclubs.school-manifest";
    private static final int VERSION = 1;

    private final String schoolId;
    private final String slug;
    private final String schoolName;
    private final String siteOrigin;
    private final String summaryUrl;

    public SchoolManifestResponse(String schoolId,
                                  String slug,
                                  String schoolName,
                                  String siteOrigin,
                                  String summaryUrl) {
        this.schoolId = schoolId;
        this.slug = slug;
        this.schoolName = schoolName;
        this.siteOrigin = siteOrigin;
        this.summaryUrl = summaryUrl;
    }

    public String getContract() { return CONTRACT; }

    public int getVersion() { return VERSION; }

    public String getSchoolId() { return schoolId; }

    public String getSlug() { return slug; }

    public String getSchoolName() { return schoolName; }

    public String getSiteOrigin() { return siteOrigin; }

    public String getSummaryUrl() { return summaryUrl; }

    public List<String> getCapabilities() { return List.of("summary.v1"); }

    public Auth getAuth() { return new Auth(); }

    /** Mobile authentication arrives with its endpoints; declaring it earlier would be a lie. */
    public static class Auth {
        public Mobile getMobile() { return new Mobile(); }
    }

    public static class Mobile {
        public boolean isSupported() { return false; }
    }
}

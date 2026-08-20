package com.example.demo.summary.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonInclude;
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
    private final Mobile mobile;

    public SchoolManifestResponse(String schoolId,
                                  String slug,
                                  String schoolName,
                                  String siteOrigin,
                                  String summaryUrl,
                                  Mobile mobile) {
        this.schoolId = schoolId;
        this.slug = slug;
        this.schoolName = schoolName;
        this.siteOrigin = siteOrigin;
        this.summaryUrl = summaryUrl;
        this.mobile = mobile;
    }

    public String getContract() { return CONTRACT; }

    public int getVersion() { return VERSION; }

    public String getSchoolId() { return schoolId; }

    public String getSlug() { return slug; }

    public String getSchoolName() { return schoolName; }

    public String getSiteOrigin() { return siteOrigin; }

    public String getSummaryUrl() { return summaryUrl; }

    public List<String> getCapabilities() {
        return mobile.isSupported()
            ? List.of("summary.v1", "mobile-auth.v1")
            : List.of("summary.v1");
    }

    public Auth getAuth() { return new Auth(mobile); }

    public static class Auth {
        private final Mobile mobile;

        public Auth(Mobile mobile) {
            this.mobile = mobile;
        }

        public Mobile getMobile() {
            return mobile;
        }
    }

    /**
     * The mobile-auth capability. When unsupported only the flag is published; when supported the
     * endpoints are named, because the contract requires an app to know where the flow starts and
     * ends before it may claim the school supports it.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Mobile {
        private final boolean supported;
        private final String startUrl;
        private final String completeUrl;
        private final String callbackUrl;
        private final List<String> codeChallengeMethods;

        private Mobile(boolean supported, String startUrl, String completeUrl, String callbackUrl) {
            this.supported = supported;
            this.startUrl = startUrl;
            this.completeUrl = completeUrl;
            this.callbackUrl = callbackUrl;
            this.codeChallengeMethods = supported ? List.of("S256") : null;
        }

        public static Mobile unsupported() {
            return new Mobile(false, null, null, null);
        }

        public static Mobile supported(String siteOrigin, String callbackUrl) {
            return new Mobile(
                true,
                siteOrigin + "/api/mobile-auth/start",
                siteOrigin + "/api/mobile-auth/complete",
                callbackUrl);
        }

        public boolean isSupported() { return supported; }

        public String getStartUrl() { return startUrl; }

        public String getCompleteUrl() { return completeUrl; }

        public String getCallbackUrl() { return callbackUrl; }

        public List<String> getCodeChallengeMethods() { return codeChallengeMethods; }
    }
}

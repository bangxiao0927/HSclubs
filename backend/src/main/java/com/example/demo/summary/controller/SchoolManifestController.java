package com.example.demo.summary.controller;

import com.example.demo.summary.config.SchoolIdentity;
import com.example.demo.summary.model.SchoolManifestResponse;
import com.example.demo.mobileauth.MobileAuthProperties;
import com.example.demo.mobileauth.MobileAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * What this deployment says it is, at {@code /.well-known/hsclubs-app.json}.
 *
 * <p>A claim, not proof. Control of this origin is proved separately by the challenge file the
 * registry already checks; this document only states which identity this origin believes it has,
 * where its summary lives, and which v1 contracts it implements. The guiding page compares every
 * field with what it already knows and refuses the school on a disagreement, which is why nothing
 * here is derived from a request header: a manifest that echoed the Host it was asked with would
 * say whatever the caller wanted it to.
 *
 * <p>Schema: contracts/v1/schemas/school-manifest.schema.json.
 */
@RestController
public class SchoolManifestController {

    private final SchoolIdentity schoolIdentity;
    private final String slug;
    private final String schoolName;
    private final MobileAuthService mobileAuthService;
    private final MobileAuthProperties mobileAuthProperties;

    public SchoolManifestController(SchoolIdentity schoolIdentity,
                                    @Value("${app.summary.slug:hsclubs}") String slug,
                                    @Value("${app.summary.school-name:HS Clubs}") String schoolName,
                                    MobileAuthService mobileAuthService,
                                    MobileAuthProperties mobileAuthProperties) {
        this.schoolIdentity = schoolIdentity;
        this.slug = slug;
        this.schoolName = schoolName;
        this.mobileAuthService = mobileAuthService;
        this.mobileAuthProperties = mobileAuthProperties;
    }

    @GetMapping(path = "/.well-known/hsclubs-app.json", produces = "application/json")
    public ResponseEntity<SchoolManifestResponse> getManifest() {
        if (!schoolIdentity.publishesV1()) {
            return ResponseEntity.notFound().build();
        }

        String origin = schoolIdentity.siteOrigin().orElseThrow();
        SchoolManifestResponse.Mobile mobile = mobileAuthService.isEnabled()
            ? SchoolManifestResponse.Mobile.supported(origin, mobileAuthProperties.primaryCallbackUrl())
            : SchoolManifestResponse.Mobile.unsupported();
        SchoolManifestResponse manifest = new SchoolManifestResponse(
            schoolIdentity.schoolId().orElseThrow(),
            slug,
            schoolName,
            origin,
            origin + "/api/v1/summary",
            mobile);

        // Short and public: the registry re-reads this on a schedule, and a manifest cached for
        // hours would keep an identity or capability change invisible long after it shipped.
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
            .body(manifest);
    }
}

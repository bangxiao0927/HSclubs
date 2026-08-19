package com.example.demo.mobileauth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * Body of a successful {@code complete}. The session itself is a cookie set on this origin; this
 * body only says where to go next and who the person now is.
 *
 * <p>A plain class rather than a record so the {@code contract}/{@code version} markers serialise
 * as fields; Jackson renders a record from its components, which these markers are not. See
 * contracts/v1/schemas/mobile-auth-complete-response.schema.json.
 */
@JsonPropertyOrder({"contract", "version", "schoolId", "returnTo", "user"})
public class MobileAuthCompleteResponse {

    private final String schoolId;
    private final String returnTo;
    private final User user;

    public MobileAuthCompleteResponse(String schoolId, String returnTo, User user) {
        this.schoolId = schoolId;
        this.returnTo = returnTo;
        this.user = user;
    }

    public String getContract() {
        return "hsclubs.mobile-auth-complete";
    }

    public int getVersion() {
        return 1;
    }

    public String getSchoolId() {
        return schoolId;
    }

    public String getReturnTo() {
        return returnTo;
    }

    public User getUser() {
        return user;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class User {
        private final String displayName;
        private final String email;
        private final List<String> roles;

        public User(String displayName, String email, List<String> roles) {
            this.displayName = displayName;
            this.email = email;
            this.roles = roles;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getEmail() {
            return email;
        }

        public List<String> getRoles() {
            return roles;
        }
    }
}

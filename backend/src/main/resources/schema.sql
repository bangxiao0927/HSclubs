-- ============================================================================
-- H2 LOCAL DEV FIXTURE -- NOT THE PRODUCTION SCHEMA.
-- ============================================================================
-- This file only runs when spring.sql.init.mode is "always", which is the case for the h2
-- Spring profile (see application-h2.yaml) and never the case in production (backend/.env
-- pins SPRING_SQL_INIT_MODE=never there; see also application.yaml's default). It has never
-- successfully executed against the real production MySQL database and is not a source of
-- truth for its schema -- most notably, clubs.achievements is CLOB here but `json NOT NULL
-- DEFAULT (json_array())` in production. That is intentional, not a typo that needs fixing:
--
--   - AchievementsTypeHandler reads/writes achievements as a plain string
--     (ps.setString / rs.getString), which is what MySQL's Connector/J driver gives back for
--     a JSON column.
--   - H2 2.x in MODE=MySQL DOES support a JSON column type, but it re-wraps whatever string is
--     written into it as an opaque JSON value; rs.getString() on that column then returns the
--     JSON-encoded *string literal* (quotes and escaping included) rather than the original
--     array text, so AchievementsTypeHandler's Jackson parse fails on it. CLOB has no such
--     wrapping, so it is the correct type for this fixture even though it would be wrong for
--     the real schema. Do not "fix" this to JSON.
--
-- This file also no longer drops any tables (that used to happen unconditionally at the top of
-- every one of these CREATE statements, in the same script that -- if SPRING_SQL_INIT_MODE were
-- ever "always" in production -- would run against real data). If h2-profile local dev needs a
-- destructive from-scratch reset on every restart, see db/h2/reset.sql, which is wired up only
-- for that profile.
-- ============================================================================

CREATE TABLE IF NOT EXISTS oauth_users (
    uid BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(200),
    display_name VARCHAR(200),
    avatar_url VARCHAR(500),
    role VARCHAR(50) NOT NULL DEFAULT 'student',
    accepted_terms_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_provider_user UNIQUE (provider, provider_user_id)
);

CREATE TABLE IF NOT EXISTS user_profiles (
    oauth_user_id BIGINT PRIMARY KEY,
    graduation_year INT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_profiles_oauth_user FOREIGN KEY (oauth_user_id) REFERENCES oauth_users(uid) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS club_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    cate_name VARCHAR(150) NOT NULL UNIQUE,
    logo VARCHAR(300),
    description TEXT
);

CREATE TABLE IF NOT EXISTS clubs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(160) NULL,
    alias_name VARCHAR(150),
    description TEXT,
    category VARCHAR(150),
    meeting_schedule VARCHAR(150),
    schedule_note TEXT,
    location VARCHAR(150),
    contact_email VARCHAR(150),
    advisor VARCHAR(150),
    image_url VARCHAR(300),
    member_count INT NOT NULL DEFAULT 0,
    achievements CLOB NOT NULL DEFAULT '[]',
    status VARCHAR(30) NOT NULL DEFAULT 'active',
    visibility VARCHAR(30) NOT NULL DEFAULT 'public',
    approved_at TIMESTAMP NULL,
    approved_by_oauth_user_id BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_clubs_approved_by FOREIGN KEY (approved_by_oauth_user_id) REFERENCES oauth_users(uid) ON DELETE SET NULL,
    CONSTRAINT fk_clubs_category FOREIGN KEY (category) REFERENCES club_category(cate_name),
    UNIQUE KEY uq_clubs_slug (slug)
);

CREATE TABLE IF NOT EXISTS club_social_medias (
    club_id BIGINT NOT NULL,
    social_type VARCHAR(50) NOT NULL,
    link_name VARCHAR(150) NOT NULL,
    link_url VARCHAR(500) NOT NULL,
    PRIMARY KEY (club_id, link_name),
    CONSTRAINT fk_social_media_club FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS club_member (
    club_id BIGINT NOT NULL,
    oauth_user_id BIGINT NOT NULL,
    role_name VARCHAR(120) NOT NULL DEFAULT 'member',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (club_id, oauth_user_id),
    CONSTRAINT fk_member_club FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE CASCADE,
    CONSTRAINT fk_member_oauth_user FOREIGN KEY (oauth_user_id) REFERENCES oauth_users(uid) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS club_membership_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    club_id BIGINT NOT NULL,
    oauth_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(30) NOT NULL DEFAULT 'pending',
    reviewed_at TIMESTAMP NULL,
    reviewed_by_oauth_user_id BIGINT NULL,
    note VARCHAR(500) NULL,
    UNIQUE KEY uq_club_membership_request (club_id, oauth_user_id),
    CONSTRAINT fk_request_club FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE CASCADE,
    CONSTRAINT fk_request_oauth_user FOREIGN KEY (oauth_user_id) REFERENCES oauth_users(uid) ON DELETE CASCADE,
    CONSTRAINT fk_request_reviewed_by FOREIGN KEY (reviewed_by_oauth_user_id) REFERENCES oauth_users(uid) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS club_tag (
    club_id BIGINT NOT NULL,
    tag VARCHAR(80) NOT NULL,
    PRIMARY KEY (club_id, tag),
    CONSTRAINT fk_tag_club FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS club_activities (
    activity_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    club_id BIGINT NOT NULL,
    activity_pic VARCHAR(400),
    activity_result VARCHAR(200),
    activity_description TEXT,
    CONSTRAINT fk_activity_club FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS calenders (
    club_id BIGINT NOT NULL,
    week_day VARCHAR(20) NOT NULL,
    meeting_time VARCHAR(50) NOT NULL,
    PRIMARY KEY (club_id, week_day),
    CONSTRAINT fk_calender_club FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE CASCADE
);

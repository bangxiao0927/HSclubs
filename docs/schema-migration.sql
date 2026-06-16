-- ============================================================
-- HSclubs Phase 1 — Multi-School Database Migration
-- Safe to run on existing database; additive only (ALTER/ADD).
-- ============================================================

-- 1. schools: upgrade to first-class entity
ALTER TABLE schools
  ADD COLUMN slug         VARCHAR(80)  NOT NULL AFTER id,
  ADD COLUMN short_name   VARCHAR(120) NULL     AFTER school_name,
  ADD COLUMN logo_url     VARCHAR(500) NULL     AFTER short_name,
  ADD COLUMN banner_url   VARCHAR(500) NULL     AFTER logo_url,
  ADD COLUMN primary_color VARCHAR(20) NULL     AFTER banner_url,
  ADD COLUMN school_domain VARCHAR(160) NULL    AFTER primary_color,
  ADD COLUMN timezone     VARCHAR(80)  NOT NULL DEFAULT 'America/Los_Angeles' AFTER school_domain,
  ADD COLUMN status       VARCHAR(30)  NOT NULL DEFAULT 'active'   AFTER timezone,
  ADD COLUMN created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER status,
  ADD COLUMN updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;

CREATE INDEX idx_schools_status ON schools(status);
CREATE UNIQUE INDEX uq_schools_domain ON schools(school_domain);
CREATE UNIQUE INDEX uq_schools_slug ON schools(slug);

UPDATE schools SET slug = 'mvhs', short_name = 'MVHS', status = 'active' WHERE id = 1;

-- 2. user_profiles: add home school FK
ALTER TABLE user_profiles
  ADD COLUMN home_school_id BIGINT NULL AFTER graduation_year,
  ADD CONSTRAINT fk_user_profiles_home_school
    FOREIGN KEY (home_school_id) REFERENCES schools(id) ON DELETE SET NULL;

CREATE INDEX idx_user_profiles_home_school_id ON user_profiles(home_school_id);

-- 3. school_users: user<->school relationship
CREATE TABLE IF NOT EXISTS school_users (
  id                       BIGINT PRIMARY KEY AUTO_INCREMENT,
  school_id                BIGINT      NOT NULL,
  oauth_user_id            BIGINT      NOT NULL,
  role                     VARCHAR(50) NOT NULL DEFAULT 'student',
  status                   VARCHAR(30) NOT NULL DEFAULT 'active',
  joined_at                TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  invited_by_oauth_user_id BIGINT      NULL,

  UNIQUE KEY uq_school_user (school_id, oauth_user_id),

  CONSTRAINT fk_school_users_school
    FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
  CONSTRAINT fk_school_users_user
    FOREIGN KEY (oauth_user_id) REFERENCES oauth_users(uid) ON DELETE CASCADE,
  CONSTRAINT fk_school_users_invited_by
    FOREIGN KEY (invited_by_oauth_user_id) REFERENCES oauth_users(uid) ON DELETE SET NULL
);

CREATE INDEX idx_school_users_role   ON school_users(role);
CREATE INDEX idx_school_users_status ON school_users(status);

-- 4. clubs: add slug, status, visibility, approval
ALTER TABLE clubs
  ADD COLUMN slug                     VARCHAR(160) NULL AFTER name,
  ADD COLUMN status                   VARCHAR(30)  NOT NULL DEFAULT 'active' AFTER school_id,
  ADD COLUMN visibility               VARCHAR(30)  NOT NULL DEFAULT 'public' AFTER status,
  ADD COLUMN approved_at              TIMESTAMP    NULL     AFTER visibility,
  ADD COLUMN approved_by_oauth_user_id BIGINT      NULL     AFTER approved_at,

  ADD CONSTRAINT fk_clubs_approved_by
    FOREIGN KEY (approved_by_oauth_user_id) REFERENCES oauth_users(uid) ON DELETE SET NULL;

CREATE UNIQUE INDEX uq_clubs_school_slug ON clubs(school_id, slug);
CREATE INDEX idx_clubs_school_status ON clubs(school_id, status);

-- 5. club_member: add joined_at timestamp
ALTER TABLE club_member
  ADD COLUMN joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER role_name;

CREATE INDEX idx_club_member_user ON club_member(oauth_user_id);

-- 6. club_membership_requests: add review tracking
ALTER TABLE club_membership_requests
  ADD COLUMN status                   VARCHAR(30) NOT NULL DEFAULT 'pending' AFTER created_at,
  ADD COLUMN reviewed_at              TIMESTAMP   NULL     AFTER status,
  ADD COLUMN reviewed_by_oauth_user_id BIGINT     NULL     AFTER reviewed_at,
  ADD COLUMN note                     VARCHAR(500) NULL    AFTER reviewed_by_oauth_user_id,

  ADD CONSTRAINT fk_membership_requests_reviewed_by
    FOREIGN KEY (reviewed_by_oauth_user_id) REFERENCES oauth_users(uid) ON DELETE SET NULL;

CREATE INDEX idx_membership_requests_status ON club_membership_requests(status);

-- 7. school_admin_invitations: invite flow
CREATE TABLE IF NOT EXISTS school_admin_invitations (
  id                       BIGINT PRIMARY KEY AUTO_INCREMENT,
  school_id                BIGINT       NOT NULL,
  email                    VARCHAR(200) NOT NULL,
  role                     VARCHAR(50)  NOT NULL DEFAULT 'school_admin',
  status                   VARCHAR(30)  NOT NULL DEFAULT 'pending',
  token                    VARCHAR(255) NOT NULL,
  expires_at               TIMESTAMP    NOT NULL,
  created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  invited_by_oauth_user_id BIGINT       NULL,

  UNIQUE KEY uq_school_invite_token (token),
  UNIQUE KEY uq_school_invite_email (school_id, email, status),

  CONSTRAINT fk_school_admin_invites_school
    FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
  CONSTRAINT fk_school_admin_invites_invited_by
    FOREIGN KEY (invited_by_oauth_user_id) REFERENCES oauth_users(uid) ON DELETE SET NULL
);

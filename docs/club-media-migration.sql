-- Production migration for club media (club_post / club_post_comment).
-- Run by hand against the MySQL production database; there is no Flyway/Liquibase in this
-- project and spring.sql.init.mode defaults to "never" in production (see backend/.env).

CREATE TABLE club_post (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    club_id                 BIGINT       NOT NULL,
    author_oauth_user_id    BIGINT       NOT NULL,
    title                   VARCHAR(140) NOT NULL,
    image_url               VARCHAR(300) NOT NULL,
    pinned_at               TIMESTAMP    NULL,
    pinned_by_oauth_user_id BIGINT       NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_post_club      FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE CASCADE,
    CONSTRAINT fk_post_author    FOREIGN KEY (author_oauth_user_id) REFERENCES oauth_users(uid) ON DELETE CASCADE,
    CONSTRAINT fk_post_pinned_by FOREIGN KEY (pinned_by_oauth_user_id) REFERENCES oauth_users(uid) ON DELETE SET NULL
);
CREATE INDEX idx_post_club_feed ON club_post (club_id, pinned_at, created_at);

CREATE TABLE club_post_comment (
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id              BIGINT       NOT NULL,
    author_oauth_user_id BIGINT       NOT NULL,
    body                 VARCHAR(300) NOT NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comment_post   FOREIGN KEY (post_id) REFERENCES club_post(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_author FOREIGN KEY (author_oauth_user_id) REFERENCES oauth_users(uid) ON DELETE CASCADE
);
CREATE INDEX idx_comment_post ON club_post_comment (post_id, created_at);

-- club_activities has zero references anywhere in the codebase, and its columns (no author,
-- no timestamps) do not fit this feature. Drop it rather than repurpose it.
DROP TABLE IF EXISTS club_activities;

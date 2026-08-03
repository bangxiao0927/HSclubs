-- Destructive, H2-local-dev-only reset. Wipes every table so a restart of the h2 Spring
-- profile always starts from the seed data in db/h2/data.sql, which is exactly the "fresh
-- sandbox on every run" experience local dev wants. This file is wired up ONLY by
-- application-h2.yaml's spring.sql.init.schema-locations; the default (production) schema
-- location is schema.sql, which no longer drops anything. Never reference this file from a
-- non-h2 profile.
DROP TABLE IF EXISTS calenders;
DROP TABLE IF EXISTS club_activities;
DROP TABLE IF EXISTS club_member;
DROP TABLE IF EXISTS club_membership_requests;
DROP TABLE IF EXISTS club_tag;
DROP TABLE IF EXISTS club_social_medias;
DROP TABLE IF EXISTS club_post_comment;
DROP TABLE IF EXISTS club_post;
DROP TABLE IF EXISTS clubs;
DROP TABLE IF EXISTS user_profiles;
DROP TABLE IF EXISTS oauth_users;
DROP TABLE IF EXISTS club_category;

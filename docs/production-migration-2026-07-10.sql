-- Production hotfix migration for the OAuth terms gate.
-- Idempotent for MySQL: adds oauth_users.accepted_terms_at when missing.

SET @column_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'oauth_users'
    AND COLUMN_NAME = 'accepted_terms_at'
);

SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE oauth_users ADD COLUMN accepted_terms_at TIMESTAMP NULL AFTER role',
  'SELECT ''oauth_users.accepted_terms_at already exists'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

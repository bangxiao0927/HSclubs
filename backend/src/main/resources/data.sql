SET @OLD_SQL_MODE = @@SQL_MODE;
SET SQL_MODE = CONCAT(@@SQL_MODE, ',NO_AUTO_VALUE_ON_ZERO');

INSERT INTO schools (id, school_name)
VALUES (0, 'Mountain View High School')
ON DUPLICATE KEY UPDATE school_name = VALUES(school_name);

INSERT INTO clubs (id, name, alias_name, description, category, meeting_schedule, location, contact_email, advisor, image_url, school_id)
VALUES
  (1, 'Spartan Robotics', 'Robotics Club', 'Building autonomous robots for regional competitions with nightly build sessions.', 'STEM', 'Thursdays 3:30PM', 'Innovation Lab', 'robotics@mvhs.org', 'Ms. Patel', NULL, 0)
ON DUPLICATE KEY UPDATE name = VALUES(name), alias_name = VALUES(alias_name), school_id = VALUES(school_id);

INSERT INTO clubs (id, name, alias_name, description, category, meeting_schedule, location, contact_email, advisor, image_url, school_id)
VALUES
  (2, 'Trail Stewards', 'Service Club', 'Restoring Shoreline trails, coordinating quarterly cleanups, and mapping biodiversity.', 'Service', 'Saturdays 9:00AM', 'Shoreline Preserve', 'trails@mvhs.org', 'Coach Ramirez', NULL, 0)
ON DUPLICATE KEY UPDATE name = VALUES(name), alias_name = VALUES(alias_name), school_id = VALUES(school_id);

SET SQL_MODE = @OLD_SQL_MODE;

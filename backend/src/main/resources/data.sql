INSERT INTO schools (id, school_name)
VALUES (1, 'Central High School')
ON DUPLICATE KEY UPDATE school_name = VALUES(school_name);

INSERT INTO schools (id, school_name)
VALUES (2, 'Westview Academy')
ON DUPLICATE KEY UPDATE school_name = VALUES(school_name);

INSERT INTO clubs (id, name, alias_name, description, category, meeting_schedule, location, contact_email, advisor, image_url, school_id)
VALUES (1, 'STEM Innovators', 'STEM Club', 'Collaborate on STEM competitions and projects.', 'STEM', 'Fridays 3:30PM', 'Room 210', 'stem@school.org', 'Dr. Chen', NULL, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), alias_name = VALUES(alias_name), school_id = VALUES(school_id);

INSERT INTO clubs (id, name, alias_name, description, category, meeting_schedule, location, contact_email, advisor, image_url, school_id)
VALUES (2, 'Community Outreach', 'Outreach Club', 'Plan monthly volunteering opportunities around town.', 'Service', 'Wednesdays 4:00PM', 'Room 105', 'outreach@school.org', 'Mr. Lewis', NULL, 2)
ON DUPLICATE KEY UPDATE name = VALUES(name), alias_name = VALUES(alias_name), school_id = VALUES(school_id);

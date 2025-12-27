SET @OLD_SQL_MODE = @@SQL_MODE;
SET SQL_MODE = CONCAT(@@SQL_MODE, ',NO_AUTO_VALUE_ON_ZERO');

INSERT INTO schools (id, school_name)
VALUES (0, 'Mountain View High School')
ON DUPLICATE KEY UPDATE school_name = VALUES(school_name);

INSERT INTO clubs (id, name, alias_name, description, category, meeting_schedule, location, contact_email, advisor, image_url, member_count, achievements, school_id)
VALUES
  (1, 'Spartan Robotics', 'Robotics Club', 'Building autonomous robots for regional competitions with nightly build sessions.', 'STEM & Innovation', 'Thu · Innovation Lab', 'Innovation Lab', 'robotics@mvhs.org', 'Dr. Patel', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Spartan%20Robotics', 72,
   JSON_ARRAY('2024 NorCal Regional finalist', 'NASA grant recipient', 'Hosts middle school scrimmages'), 0),
  (2, 'Trail Stewards', 'Service Club', 'Restoring Shoreline trails, coordinating quarterly cleanups, and mapping biodiversity.', 'Service & Leadership', 'Sat · Shoreline Trail', 'Shoreline Preserve', 'trails@mvhs.org', 'Ms. Gomez', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Trail%20Stewards', 64,
   JSON_ARRAY('2.3 miles of trail restored', 'Quarterly city collaboration award', 'Leads volunteer onboarding'), 0),
  (3, 'Golden Sound Collective', 'Media Studio', 'Student-run media studio producing podcasts, live streams, and halftime shows.', 'Creative Arts & Media', 'Tue · Studio 204', 'Studio 204', 'gsc@mvhs.org', 'Mr. Rios', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Golden%20Sound%20Collective', 58,
   JSON_ARRAY('Weekly Spartan Stories podcast', 'Live-stream crew for athletics', 'Adobe Youth Voices finalist'), 0),
  (4, 'Girls Who Code', NULL, 'Inclusive coding collective focusing on full-stack projects and mentorship.', 'STEM & Innovation', 'Mon · Lab 3', 'Lab 3', 'gwc@mvhs.org', 'Ms. Nguyen', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Girls%20Who%20Code', 47,
   JSON_ARRAY('Shipped 4 community apps', 'Silicon Valley Demo Day finalist', 'Peer mentor network'), 0),
  (5, 'Model United Nations', 'MUN', 'Delegations researching global issues, drafting resolutions, and hosting local conferences.', 'Service & Leadership', 'Wed · Library', 'Library', 'mun@mvhs.org', 'Ms. Chandra', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Model%20United%20Nations', 33,
   JSON_ARRAY('Best Delegate @ UC Berkeley MUN', 'Hosted district diplomacy night', 'Partners with city youth council'), 0),
  (6, 'Gaming Strategy Lab', NULL, 'Game design and esports strategy team running scrims, analytics, and shoutcasting.', 'Creative Arts & Media', 'Fri · Room 132', 'Room 132', 'gsl@mvhs.org', 'Mr. Walters', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Gaming%20Strategy%20Lab', 29,
   JSON_ARRAY('CIF esports playoff run', 'Unity game jam finalist', 'Shoutcasted district finals'), 0)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  alias_name = VALUES(alias_name),
  description = VALUES(description),
  category = VALUES(category),
  meeting_schedule = VALUES(meeting_schedule),
  location = VALUES(location),
  contact_email = VALUES(contact_email),
  advisor = VALUES(advisor),
  image_url = VALUES(image_url),
  member_count = VALUES(member_count),
  achievements = VALUES(achievements),
  school_id = VALUES(school_id);

SET SQL_MODE = @OLD_SQL_MODE;

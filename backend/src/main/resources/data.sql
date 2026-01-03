SET @OLD_SQL_MODE = @@SQL_MODE;
SET SQL_MODE = CONCAT(@@SQL_MODE, ',NO_AUTO_VALUE_ON_ZERO');

INSERT INTO oauth_users (uid, provider, provider_user_id, email, display_name, avatar_url, role)
VALUES
  (1, 'google', 'google-123', 'maya.chen@example.com', 'Maya Chen', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Maya', 'student'),
  (2, 'google', 'google-456', 'leo.martinez@example.com', 'Leo Martinez', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Leo', 'student'),
  (3, 'google', 'google-789', 'priya.singh@example.com', 'Priya Singh', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Priya', 'advisor'),
  (4, 'google', 'google-321', 'apatel@mvhs.org', 'Dr. Patel', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Patel', 'staff')
ON DUPLICATE KEY UPDATE
  email = VALUES(email),
  display_name = VALUES(display_name),
  avatar_url = VALUES(avatar_url),
  role = VALUES(role),
  last_login_at = CURRENT_TIMESTAMP;

INSERT INTO schools (id, school_name)
VALUES (1, 'Mountain View High School')
ON DUPLICATE KEY UPDATE school_name = VALUES(school_name);

INSERT INTO club_category (id, cate_name, logo, description)
VALUES
  (1, 'STEM & Innovation', 'https://cdn.example.com/logos/stem.svg', 'Engineering, robotics, science, and technology-focused clubs.'),
  (2, 'Service & Leadership', 'https://cdn.example.com/logos/service.svg', 'Clubs centered on volunteering, civic engagement, and leadership.'),
  (3, 'Creative Arts & Media', 'https://cdn.example.com/logos/arts.svg', 'Visual arts, performing arts, and multimedia storytelling clubs.')
ON DUPLICATE KEY UPDATE
  logo = VALUES(logo),
  description = VALUES(description);

INSERT INTO clubs (id, name, alias_name, description, category, meeting_schedule, location, contact_email, advisor, image_url, member_count, achievements, school_id)
VALUES
  (1, 'Spartan Robotics', 'Robotics Club', 'Building autonomous robots for regional competitions with nightly build sessions.', 'STEM & Innovation', 'Thu · Innovation Lab', 'Innovation Lab', 'robotics@mvhs.org', 'Dr. Patel', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Spartan%20Robotics', 72,
   JSON_ARRAY('2024 NorCal Regional finalist', 'NASA grant recipient', 'Hosts middle school scrimmages'), 1),
  (2, 'Trail Stewards', 'Service Club', 'Restoring Shoreline trails, coordinating quarterly cleanups, and mapping biodiversity.', 'Service & Leadership', 'Sat · Shoreline Trail', 'Shoreline Preserve', 'trails@mvhs.org', 'Ms. Gomez', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Trail%20Stewards', 64,
   JSON_ARRAY('2.3 miles of trail restored', 'Quarterly city collaboration award', 'Leads volunteer onboarding'), 1),
  (3, 'Golden Sound Collective', 'Media Studio', 'Student-run media studio producing podcasts, live streams, and halftime shows.', 'Creative Arts & Media', 'Tue · Studio 204', 'Studio 204', 'gsc@mvhs.org', 'Mr. Rios', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Golden%20Sound%20Collective', 58,
   JSON_ARRAY('Weekly Spartan Stories podcast', 'Live-stream crew for athletics', 'Adobe Youth Voices finalist'), 1),
  (4, 'Girls Who Code', NULL, 'Inclusive coding collective focusing on full-stack projects and mentorship.', 'STEM & Innovation', 'Mon · Lab 3', 'Lab 3', 'gwc@mvhs.org', 'Ms. Nguyen', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Girls%20Who%20Code', 47,
   JSON_ARRAY('Shipped 4 community apps', 'Silicon Valley Demo Day finalist', 'Peer mentor network'), 1),
  (5, 'Model United Nations', 'MUN', 'Delegations researching global issues, drafting resolutions, and hosting local conferences.', 'Service & Leadership', 'Wed · Library', 'Library', 'mun@mvhs.org', 'Ms. Chandra', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Model%20United%20Nations', 33,
   JSON_ARRAY('Best Delegate @ UC Berkeley MUN', 'Hosted district diplomacy night', 'Partners with city youth council'), 1),
  (6, 'Gaming Strategy Lab', NULL, 'Game design and esports strategy team running scrims, analytics, and shoutcasting.', 'Creative Arts & Media', 'Fri · Room 132', 'Room 132', 'gsl@mvhs.org', 'Mr. Walters', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Gaming%20Strategy%20Lab', 29,
   JSON_ARRAY('CIF esports playoff run', 'Unity game jam finalist', 'Shoutcasted district finals'), 1)
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

INSERT INTO club_member (club_id, oauth_user_id, role_name)
VALUES
  (1, 1, 'President'),
  (1, 2, 'Build Lead'),
  (2, 2, 'Coordinator'),
  (3, 3, 'Producer'),
  (4, 4, 'Captain')
ON DUPLICATE KEY UPDATE
  role_name = VALUES(role_name);

INSERT INTO club_social_medias (club_id, social_type, link_name, link_url)
VALUES
  (1, 'instagram', 'Instagram', 'https://instagram.com/spartanrobotics'),
  (1, 'discord', 'Discord', 'https://discord.gg/spartanrobotics'),
  (2, 'instagram', 'Instagram', 'https://instagram.com/trailstewards'),
  (3, 'youtube', 'YouTube', 'https://youtube.com/goldensound'),
  (5, 'x', 'X', 'https://x.com/mvhsmun')
ON DUPLICATE KEY UPDATE
  social_type = VALUES(social_type),
  link_url = VALUES(link_url);

INSERT INTO club_tag (club_id, tag)
VALUES
  (1, 'robotics'),
  (1, 'engineering'),
  (2, 'service'),
  (3, 'media'),
  (4, 'coding'),
  (5, 'leadership')
ON DUPLICATE KEY UPDATE
  tag = VALUES(tag);

INSERT INTO club_activities (activity_id, club_id, activity_pic, activity_result, activity_description)
VALUES
  (101, 1, 'https://cdn.example.com/activities/robotics-finals.jpg', 'Regional Finalist', 'Completed a 36-hour build sprint for NorCal regionals.'),
  (102, 2, 'https://cdn.example.com/activities/trail-clean.jpg', 'Trail Restored', 'Cleared invasive species along the Shoreline trail.'),
  (103, 3, 'https://cdn.example.com/activities/podcast.jpg', 'Podcast Launch', 'Released season 2 of Spartan Stories Spotify show.')
ON DUPLICATE KEY UPDATE
  club_id = VALUES(club_id),
  activity_pic = VALUES(activity_pic),
  activity_result = VALUES(activity_result),
  activity_description = VALUES(activity_description);

INSERT INTO calenders (club_id, week_day, meeting_time)
VALUES
  (1, 'Thursday', '6:00 PM'),
  (2, 'Saturday', '9:00 AM'),
  (3, 'Tuesday', '4:00 PM'),
  (4, 'Monday', '3:30 PM'),
  (5, 'Wednesday', '5:00 PM'),
  (6, 'Friday', '7:00 PM')
ON DUPLICATE KEY UPDATE
  meeting_time = VALUES(meeting_time);

SET SQL_MODE = @OLD_SQL_MODE;

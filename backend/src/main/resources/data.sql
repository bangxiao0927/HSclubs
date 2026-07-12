-- H2-compatible seed data (MVHS clubs)

INSERT INTO oauth_users (uid, provider, provider_user_id, email, display_name, avatar_url, role) VALUES
  (1, 'google', 'google-123', 'maya.chen@example.com', 'Maya Chen', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Maya', 'student'),
  (2, 'google', 'google-456', 'leo.martinez@example.com', 'Leo Martinez', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Leo', 'student'),
  (3, 'google', 'google-789', 'priya.singh@example.com', 'Priya Singh', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Priya', 'advisor'),
  (4, 'google', 'google-321', 'apatel@mvhs.org', 'Dr. Patel', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Patel', 'staff');

INSERT INTO club_category (id, cate_name, logo, description) VALUES
  (1, 'STEM & Innovation', 'https://cdn.example.com/logos/stem.svg', 'Engineering, robotics, science, and technology-focused clubs.'),
  (2, 'Service & Leadership', 'https://cdn.example.com/logos/service.svg', 'Clubs centered on volunteering, civic engagement, and leadership.'),
  (3, 'Creative Arts & Media', 'https://cdn.example.com/logos/arts.svg', 'Visual arts, performing arts, and multimedia storytelling clubs.'),
  (4, 'Culture & Identity', 'https://cdn.example.com/logos/culture.svg', 'Clubs organized around heritage, culture, language, and identity communities.'),
  (5, 'Wellness & Athletics', 'https://cdn.example.com/logos/wellness.svg', 'Athletics, physical wellness, recreation, and mental health clubs.'),
  (6, 'Competition & Strategy', 'https://cdn.example.com/logos/competition.svg', 'Debate, games, olympiads, and strategy-focused clubs.');

INSERT INTO clubs (id, name, alias_name, description, category, meeting_schedule, location, contact_email, advisor, member_count, achievements) VALUES
  (1, 'Robotics Club', 'Bot Builders', 'Design, build, and compete with VEX and FRC robots. Students learn mechanical design, programming, and teamwork through regional competitions.', 'STEM & Innovation', 'Tuesdays & Thursdays after school', 'Engineering Lab 201', 'robotics@mvhs.edu', 'Mr. Chen', 42, '["NorCal Regional Finalist 2025","Rookie All-Star Award"]'),
  (2, 'Art Collective', 'Studio Club', 'Explore painting, digital art, and sculpture. Plan gallery exhibits and create portfolio work for college applications.', 'Creative Arts & Media', 'Thursdays at lunch', 'Art Room 305', 'artcollective@mvhs.edu', 'Ms. Rivera', 28, '["Campus Mural Project 2025","Student Gallery Night"]'),
  (3, 'Service Council', 'Volunteer Club', 'Coordinate volunteer drives, fundraisers, and local service projects. Partner with community organizations for meaningful impact.', 'Service & Leadership', 'Mondays before school', 'Student Center', 'service@mvhs.edu', 'Mrs. Thompson', 35, '["Food Drive Completed","Peer Tutoring Program Launched"]'),
  (4, 'Debate Club', 'Forensics', 'Compete in parliamentary and Lincoln-Douglas debate. Develop critical thinking, public speaking, and argumentation skills.', 'Competition & Strategy', 'Wednesdays after school', 'Room 412', 'debate@mvhs.edu', 'Mr. Harrison', 24, '["State Qualifiers 2025","Best Speaker Award"]'),
  (5, 'Chess Club', 'Spartan Chess', 'Play casual and competitive chess. Participate in regional tournaments and learn advanced strategies.', 'Competition & Strategy', 'Fridays at lunch', 'Library', 'chess@mvhs.edu', 'Mr. Kowalski', 18, '[]'),
  (6, 'Environmental Action', 'Green Team', 'Lead sustainability initiatives on campus. Manage recycling programs, organize cleanups, and advocate for eco-friendly policies.', 'Service & Leadership', 'Tuesdays at lunch', 'Room 108', 'greenteam@mvhs.edu', 'Ms. Okonkwo', 31, '["Campus Recycling Program","Tree Planting Drive"]'),
  (7, 'Coding Club', 'MVHS Devs', 'Learn web development, Python, and competitive programming. Build projects, prepare for hackathons, and mentor beginners.', 'STEM & Innovation', 'Mondays & Wednesdays after school', 'Computer Lab 103', 'coding@mvhs.edu', 'Mr. Kumar', 38, '["Hackathon 3rd Place","App Demo Day"]'),
  (8, 'Drama Club', 'Spartan Stage', 'Produce full-length plays and one-act festivals. Open to actors, stage crew, lighting, and set design.', 'Creative Arts & Media', 'Tuesdays & Thursdays 4-6pm', 'Theater', 'drama@mvhs.edu', 'Ms. Alvarez', 45, '["Fall Play: The Crucible","Spring Musical: Grease"]'),
  (9, 'Key Club', 'MVHS Kiwanis', 'International student-led service organization. Focus on leadership development through community service.', 'Service & Leadership', 'Every other Wednesday at lunch', 'Room 210', 'keyclub@mvhs.edu', 'Mrs. Davis', 52, '["1000+ Service Hours","Trick-or-Treat for UNICEF"]'),
  (10, 'Photography Club', 'Shutterbugs', 'Learn digital and film photography techniques. Organize photo walks, exhibitions, and collaborate with yearbook.', 'Creative Arts & Media', 'Fridays after school', 'Room 312', 'photo@mvhs.edu', 'Mr. Lee', 15, '["Campus Photo Exhibition","Portrait Workshop Series"]'),
  (11, 'Science Olympiad', 'SciOly', 'Compete in 23 events spanning biology, chemistry, physics, earth science, and engineering. Prepare for regional and state tournaments.', 'STEM & Innovation', 'Wednesdays 3:30-5:30pm + Saturdays', 'Science Wing', 'scioly@mvhs.edu', 'Dr. Nakamura', 33, '["Regional Champions 2025","State Qualifiers"]'),
  (12, 'Model UN', 'MUN', 'Simulate United Nations committees and debate global issues. Develop diplomacy, research, and public speaking skills.', 'Competition & Strategy', 'Thursdays after school', 'Room 220', 'mun@mvhs.edu', 'Mr. Grant', 28, '["Best Delegation at SCVMUN","Hosted MVHS MUN Conference"]'),
  (13, 'Culinary Club', 'Spartan Chefs', 'Learn cooking techniques from around the world. Host bake sales, cooking competitions, and food-themed events.', 'Culture & Identity', 'Tuesdays after school', 'Culinary Arts Room', 'culinary@mvhs.edu', 'Chef Martinez', 20, '["International Food Fair","Bake Sale Fundraiser"]'),
  (14, 'Photography Club', 'MVHS Yearbook', 'Capture school events, sports, and student life. Produce the annual yearbook with photography, design, and journalism.', 'Creative Arts & Media', 'Daily during advisory', 'Room 105', 'yearbook@mvhs.edu', 'Ms. Foster', 22, '["Yearbook Distribution 2025","Walsworth Gallery Recognition"]'),
  (15, 'Basketball Club', 'Spartan Hoops', 'Open basketball for all skill levels. Weekly pickup games, skill workshops, and intramural tournaments.', 'Wellness & Athletics', 'Mondays & Fridays after school', 'Gym', 'hoops@mvhs.edu', 'Coach Williams', 40, '[]'),
  (16, 'Yoga & Wellness', 'Mindful Spartans', 'Practice yoga, meditation, and stress management techniques. Promote mental health awareness on campus.', 'Wellness & Athletics', 'Wednesdays at lunch', 'Wellness Center', 'yoga@mvhs.edu', 'Ms. Patel', 25, '["Mental Health Awareness Week","Campus Meditation Garden"]'),
  (17, 'Black Student Union', 'BSU', 'Celebrate Black culture, history, and achievement. Create community, lead discussions, and organize cultural events.', 'Culture & Identity', 'Thursdays at lunch', 'Room 115', 'bsu@mvhs.edu', 'Ms. Jones', 30, '["Black History Month Assembly","Soul Food Potluck"]'),
  (18, 'LatinX Club', 'LatinX Unidos', 'Promote Latin American culture and heritage. Host cultural celebrations, dance workshops, and community events.', 'Culture & Identity', 'Tuesdays at lunch', 'Room 118', 'latinx@mvhs.edu', 'Sra. Garcia', 27, '["Dia de los Muertos Celebration","Latin Dance Night"]'),
  (19, 'Anime & Manga Club', 'Otaku Circle', 'Watch and discuss anime, share manga recommendations, and explore Japanese pop culture. Host cosplay events.', 'Culture & Identity', 'Fridays at lunch', 'Room 230', 'anime@mvhs.edu', 'Mr. Tanaka', 35, '["Cosplay Contest","Anime Movie Marathon"]'),
  (20, 'Math Club', 'Mathletes', 'Explore advanced mathematics beyond the curriculum. Prepare for AMC, AIME, and other math competitions.', 'STEM & Innovation', 'Mondays after school', 'Room 405', 'mathclub@mvhs.edu', 'Dr. Zhang', 19, '["AMC 12 Qualifiers","Math Tutoring Program"]');

INSERT INTO club_social_medias (club_id, social_type, link_name, link_url) VALUES
  (1, 'instagram', 'Instagram', 'https://www.instagram.com/nasa/'),
  (7, 'instagram', 'Instagram', 'https://www.instagram.com/github/'),
  (9, 'instagram', 'Instagram', 'https://www.instagram.com/keyclubint/'),
  (10, 'instagram', 'Instagram', 'https://www.instagram.com/instagram/'),
  (11, 'instagram', 'Instagram', 'https://www.instagram.com/nasa/'),
  (19, 'instagram', 'Instagram', 'https://www.instagram.com/crunchyroll/');

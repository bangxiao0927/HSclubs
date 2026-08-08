package com.example.demo.club.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.club.model.Club;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

// The default test datasource ("hsclubs_test") is shared across Spring contexts in the same
// JVM (DB_CLOSE_DELAY=-1), and setUp() below rebuilds oauth_users without the created_at /
// last_login_at / accepted_terms_at columns that OAuthUserMapperTest and AuthServiceTest expect
// on their oauth_users tables. Running on the shared database would leave that truncated table
// behind for whichever of those tests runs next without re-creating it first, so this test gets
// its own named in-memory database instead.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:club_mapper_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class ClubMapperTest {

    @Autowired
    private ClubMapper clubMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS club_member");
        jdbcTemplate.execute("DROP TABLE IF EXISTS club_social_medias");
        jdbcTemplate.execute("DROP TABLE IF EXISTS clubs");
        jdbcTemplate.execute("DROP TABLE IF EXISTS oauth_users");
        jdbcTemplate.execute("""
            CREATE TABLE oauth_users (
                uid BIGINT PRIMARY KEY AUTO_INCREMENT,
                provider VARCHAR(50) NOT NULL,
                provider_user_id VARCHAR(150) NOT NULL,
                email VARCHAR(255),
                display_name VARCHAR(255),
                avatar_url VARCHAR(500),
                role VARCHAR(50)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE clubs (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                name VARCHAR(150) NOT NULL,
                slug VARCHAR(160),
                alias_name VARCHAR(150),
                description CLOB,
                category VARCHAR(150),
                meeting_schedule VARCHAR(150),
                schedule_note CLOB,
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
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE club_social_medias (
                club_id BIGINT NOT NULL,
                social_type VARCHAR(50) NOT NULL,
                link_name VARCHAR(150) NOT NULL,
                link_url VARCHAR(500) NOT NULL,
                PRIMARY KEY (club_id, link_name)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE club_member (
                club_id BIGINT NOT NULL,
                oauth_user_id BIGINT NOT NULL,
                role_name VARCHAR(120) NOT NULL DEFAULT 'member',
                joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (club_id, oauth_user_id)
            )
            """);
    }

    @Test
    void lockClubIdForUpdateReturnsTheIdWhenTheClubExists() {
        jdbcTemplate.update(
            "INSERT INTO clubs (id, name, category, status) VALUES (1, 'Chess Club', 'Competition & Strategy', 'active')");

        Long lockedId = clubMapper.lockClubIdForUpdate(1L);

        assertThat(lockedId).isEqualTo(1L);
    }

    @Test
    void lockClubIdForUpdateReturnsNullForAMissingClub() {
        Long lockedId = clubMapper.lockClubIdForUpdate(999L);

        assertThat(lockedId).isNull();
    }

    // insertMember is the assign-a-role statement, so it deliberately overwrites role_name --
    // that is how a president is assigned. Approving a join request must not use it: the
    // applicant may already be in the club, and downgrading the club's own president to member
    // is the worst case of that.
    @Test
    void insertMemberIfAbsentLeavesAnExistingPresidentRoleAlone() {
        jdbcTemplate.update("INSERT INTO oauth_users (uid, provider, provider_user_id) VALUES (30, 'google', 'g-30')");
        jdbcTemplate.update("INSERT INTO club_member (club_id, oauth_user_id, role_name) VALUES (3, 30, 'president')");

        clubMapper.insertMemberIfAbsent(3L, 30L, "member");

        assertThat(roleOf(3L, 30L)).isEqualTo("president");
    }

    @Test
    void insertMemberIfAbsentStillAddsAMemberWhoIsNotInTheClubYet() {
        jdbcTemplate.update("INSERT INTO oauth_users (uid, provider, provider_user_id) VALUES (31, 'google', 'g-31')");

        clubMapper.insertMemberIfAbsent(3L, 31L, "member");

        assertThat(roleOf(3L, 31L)).isEqualTo("member");
    }

    @Test
    void insertMemberStillOverwritesTheRoleSoPresidentAssignmentKeepsWorking() {
        jdbcTemplate.update("INSERT INTO oauth_users (uid, provider, provider_user_id) VALUES (32, 'google', 'g-32')");
        jdbcTemplate.update("INSERT INTO club_member (club_id, oauth_user_id, role_name) VALUES (3, 32, 'member')");

        clubMapper.insertMember(3L, 32L, "president");

        assertThat(roleOf(3L, 32L)).isEqualTo("president");
    }

    private String roleOf(long clubId, long oauthUserId) {
        return jdbcTemplate.queryForObject(
            "SELECT role_name FROM club_member WHERE club_id = ? AND oauth_user_id = ?",
            String.class, clubId, oauthUserId);
    }

    // The server-side counterpart of frontend/src/utils/clubSearch.ts: each word may match a
    // different column, so the two cannot disagree about what a student's query means.
    @Test
    void searchMatchesWordsInAnyOrderAndAcrossDifferentColumns() {
        insertSearchableClub();

        assertThat(clubMapper.search(null, null, null, null, List.of("club", "chess"), 0, 10))
            .extracting(Club::getName)
            .containsExactly("Chess Club");
        assertThat(clubMapper.search(null, null, null, null, List.of("chess", "214"), 0, 10))
            .hasSize(1);
    }

    // The example in docs/API.md: the words live in the name and the meeting schedule.
    @Test
    void searchMatchesAWordThatOnlyAppearsInTheMeetingSchedule() {
        insertSearchableClub();

        assertThat(clubMapper.search(null, null, null, null, List.of("chess", "wednesday"), 0, 10))
            .hasSize(1);
    }

    @Test
    void searchStillRequiresEveryWordToMatchSomething() {
        insertSearchableClub();

        assertThat(clubMapper.search(null, null, null, null, List.of("chess", "badminton"), 0, 10))
            .isEmpty();
    }

    @Test
    void searchWithNoWordsReturnsEveryActiveClub() {
        insertSearchableClub();

        assertThat(clubMapper.search(null, null, null, null, List.of(), 0, 10)).hasSize(1);
    }

    // Archiving must be a status-only write and must take the club out of every listing, while
    // leaving the row (and its posts and roster) intact so the decision stays reversible.
    @Test
    void updateStatusChangesOnlyTheStatusColumnAndHidesTheClubFromListings() {
        insertSearchableClub();

        clubMapper.updateStatus(4L, "archived");

        assertThat(clubMapper.findAllPaginated(0, 10)).isEmpty();
        assertThat(clubMapper.search(null, null, null, null, List.of("chess"), 0, 10)).isEmpty();
        Club stored = clubMapper.findById(4L);
        assertThat(stored).isNotNull();
        assertThat(stored.getStatus()).isEqualTo("archived");
        assertThat(stored.getName()).isEqualTo("Chess Club");
        assertThat(stored.getDescription()).isEqualTo("Casual and competitive play");
        assertThat(stored.getAdvisor()).isEqualTo("Ms. Lee");
    }

    @Test
    void updateStatusCanPutAnArchivedClubBackIntoTheDirectory() {
        insertSearchableClub();
        clubMapper.updateStatus(4L, "archived");

        clubMapper.updateStatus(4L, "active");

        assertThat(clubMapper.findAllPaginated(0, 10)).hasSize(1);
    }

    // ---- Bounded public queries (#104) ----

    @Test
    void findPopularOrdersByMemberCountAndLimitsInSql() {
        insertClub(10, "Quiet Club", "STEM & Innovation");
        insertClub(11, "Busy Club", "STEM & Innovation");
        insertMembers(11, 100, 3);
        insertMembers(10, 200, 1);

        List<Club> popular = clubMapper.findPopular(1);

        assertThat(popular).extracting(Club::getName).containsExactly("Busy Club");
        assertThat(popular.get(0).getMemberCount()).isEqualTo(3);
    }

    @Test
    void findPopularInCategoriesFiltersByCategoryAndExcludesJoinedClubs() {
        insertClub(10, "Robotics", "STEM & Innovation");
        insertClub(11, "Chess", "Competition & Strategy");
        insertClub(12, "Rocketry", "STEM & Innovation");

        List<Club> recommended =
            clubMapper.findPopularInCategories(List.of("STEM & Innovation"), List.of(10L), 10);

        assertThat(recommended).extracting(Club::getName).containsExactly("Rocketry");
    }

    @Test
    void findPopularInCategoriesWorksWhenTheViewerHasJoinedNothingYet() {
        insertClub(10, "Robotics", "STEM & Innovation");

        List<Club> recommended =
            clubMapper.findPopularInCategories(List.of("STEM & Innovation"), List.of(), 10);

        assertThat(recommended).hasSize(1);
    }

    // The calendar renders scalar fields only, so its query must not drag the description and
    // achievements CLOB along for every club.
    @Test
    void findCalendarEntriesReturnsTheRenderedFieldsWithoutTheHeavyOnes() {
        insertSearchableClub();

        List<Club> entries = clubMapper.findCalendarEntries();

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.getName()).isEqualTo("Chess Club");
            assertThat(entry.getMeetingSchedule()).isEqualTo("Wednesday lunch");
            assertThat(entry.getLocation()).isEqualTo("Room 214");
            assertThat(entry.getAdvisor()).isEqualTo("Ms. Lee");
            assertThat(entry.getDescription()).isNull();
        });
    }

    @Test
    void findSummaryProjectionsCountsMembersWithoutSelectingWholeClubRows() {
        insertClub(10, "Robotics", "STEM & Innovation");
        insertMembers(10, 300, 2);

        assertThat(clubMapper.findSummaryProjections()).singleElement().satisfies(projection -> {
            assertThat(projection.getName()).isEqualTo("Robotics");
            assertThat(projection.getCategory()).isEqualTo("STEM & Innovation");
            assertThat(projection.getMemberCount()).isEqualTo(2);
        });
    }

    @Test
    void theseQueriesAllHideNonActiveClubs() {
        insertClub(10, "Archived Club", "STEM & Innovation");
        clubMapper.updateStatus(10L, "archived");

        assertThat(clubMapper.findPopular(10)).isEmpty();
        assertThat(clubMapper.findPopularInCategories(List.of("STEM & Innovation"), List.of(), 10)).isEmpty();
        assertThat(clubMapper.findCalendarEntries()).isEmpty();
        assertThat(clubMapper.findSummaryProjections()).isEmpty();
    }

    private void insertClub(long id, String name, String category) {
        jdbcTemplate.update(
            "INSERT INTO clubs (id, name, category, status) VALUES (?, ?, ?, 'active')", id, name, category);
    }

    private void insertMembers(long clubId, long firstUserId, int count) {
        for (int i = 0; i < count; i++) {
            long userId = firstUserId + i;
            jdbcTemplate.update(
                "INSERT INTO oauth_users (uid, provider, provider_user_id) VALUES (?, 'google', ?)",
                userId, "g-" + userId);
            jdbcTemplate.update(
                "INSERT INTO club_member (club_id, oauth_user_id) VALUES (?, ?)", clubId, userId);
        }
    }

    private void insertSearchableClub() {
        jdbcTemplate.update("""
            INSERT INTO clubs (id, name, category, description, location, advisor,
                               meeting_schedule, contact_email, status)
            VALUES (4, 'Chess Club', 'Competition & Strategy', 'Casual and competitive play',
                    'Room 214', 'Ms. Lee', 'Wednesday lunch', 'chess@example.com', 'active')
            """);
    }

    @Test
    void findAllPaginatedReportsMemberCountFromClubMemberTableNotTheStaleColumn() {
        jdbcTemplate.update(
            "INSERT INTO clubs (id, name, category, member_count, status) VALUES (1, 'Chess Club', 'Competition & Strategy', 0, 'active')");
        jdbcTemplate.update("INSERT INTO oauth_users (uid, provider, provider_user_id) VALUES (10, 'google', 'g-10')");
        jdbcTemplate.update("INSERT INTO oauth_users (uid, provider, provider_user_id) VALUES (11, 'google', 'g-11')");
        jdbcTemplate.update("INSERT INTO club_member (club_id, oauth_user_id) VALUES (1, 10)");
        jdbcTemplate.update("INSERT INTO club_member (club_id, oauth_user_id) VALUES (1, 11)");

        List<Club> clubs = clubMapper.findAllPaginated(0, 10);

        assertThat(clubs).singleElement().satisfies(club -> assertThat(club.getMemberCount()).isEqualTo(2));
    }

    @Test
    void findByIdReflectsMemberCountImmediatelyAfterApprovingAMembershipRequest() {
        jdbcTemplate.update(
            "INSERT INTO clubs (id, name, category, member_count, status) VALUES (2, 'Robotics', 'STEM & Innovation', 0, 'active')");
        jdbcTemplate.update("INSERT INTO oauth_users (uid, provider, provider_user_id) VALUES (20, 'google', 'g-20')");

        // This is exactly what ClubService#approveMembershipRequest does: insert the new member
        // row without ever touching clubs.member_count.
        clubMapper.insertMember(2L, 20L, "member");

        Club club = clubMapper.findById(2L);

        assertThat(club.getMemberCount()).isEqualTo(1);
    }

    // member_count is derived from club_member (see ClubMapper.xml's BaseColumnList correlated
    // subquery) and update()/insert() intentionally no longer write it.
    @Test
    void updateIgnoresAClientSuppliedMemberCountInsteadOfOverwritingTheDerivedValue() {
        jdbcTemplate.update(
            "INSERT INTO clubs (id, name, category, member_count, status) "
                + "VALUES (1, 'Chess Club', 'Competition & Strategy', 0, 'active')");
        jdbcTemplate.update("INSERT INTO oauth_users (uid, provider, provider_user_id) VALUES (10, 'google', 'g-10')");
        jdbcTemplate.update("INSERT INTO oauth_users (uid, provider, provider_user_id) VALUES (11, 'google', 'g-11')");
        jdbcTemplate.update("INSERT INTO club_member (club_id, oauth_user_id) VALUES (1, 10)");
        jdbcTemplate.update("INSERT INTO club_member (club_id, oauth_user_id) VALUES (1, 11)");

        Club update = clubMapper.findById(1L);
        update.setName("Chess Club (renamed)");
        // Simulates a stale/forged client payload trying to set the count directly;
        // update() must not let this reach the member_count column at all.
        update.setMemberCount(999);

        clubMapper.update(update);

        Club reloaded = clubMapper.findById(1L);
        assertThat(reloaded.getName()).isEqualTo("Chess Club (renamed)");
        assertThat(reloaded.getMemberCount()).isEqualTo(2);

        Integer rawColumnValue = jdbcTemplate.queryForObject(
            "SELECT member_count FROM clubs WHERE id = 1", Integer.class);
        assertThat(rawColumnValue).isEqualTo(0);
    }

    @Test
    void insertIgnoresAClientSuppliedMemberCountAndLeavesTheColumnAtItsDefault() {
        Club club = new Club();
        club.setName("Robotics");
        club.setCategory("STEM & Innovation");
        club.setStatus("active");
        club.setVisibility("public");
        club.setAchievements(List.of());
        // A brand-new club has no members yet regardless of what a client sends here.
        club.setMemberCount(500);

        clubMapper.insert(club);

        Integer rawColumnValue = jdbcTemplate.queryForObject(
            "SELECT member_count FROM clubs WHERE id = " + club.getId(), Integer.class);
        assertThat(rawColumnValue).isEqualTo(0);
        assertThat(clubMapper.findById(club.getId()).getMemberCount()).isEqualTo(0);
    }

    // image_url is only ever supposed to change through the dedicated, authenticated image
    // upload path (ClubImageController -> ClubService#updateImageUrl -> updateImageUrl below),
    // never through the general club-editing form. A club manager could otherwise set their
    // own club's imageUrl in a PUT /api/clubs/{id} body to another club's real, guessable
    // /uploads/club-posts/<uuid>.jpg path and have it silently adopted.
    @Test
    void updateIgnoresAClientSuppliedImageUrlInsteadOfOverwritingTheStoredValue() {
        jdbcTemplate.update(
            "INSERT INTO clubs (id, name, category, status, image_url) "
                + "VALUES (1, 'Chess Club', 'Competition & Strategy', 'active', '/uploads/club-posts/original-uuid.jpg')");

        Club update = clubMapper.findById(1L);
        update.setName("Chess Club (renamed)");
        // Simulates a manager's PUT body naming another club's real image path; update() must
        // not let this reach the image_url column at all.
        update.setImageUrl("/uploads/club-posts/attacker-guessed-uuid.jpg");

        clubMapper.update(update);

        Club reloaded = clubMapper.findById(1L);
        assertThat(reloaded.getName()).isEqualTo("Chess Club (renamed)");
        assertThat(reloaded.getImageUrl()).isEqualTo("/uploads/club-posts/original-uuid.jpg");
    }

    @Test
    void updateImageUrlChangesOnlyTheImageUrlColumn() {
        jdbcTemplate.update(
            "INSERT INTO clubs (id, name, category, status, image_url) "
                + "VALUES (1, 'Chess Club', 'Competition & Strategy', 'active', '/uploads/club-posts/original-uuid.jpg')");

        clubMapper.updateImageUrl(1L, "/uploads/club-posts/new-uuid.jpg");

        Club reloaded = clubMapper.findById(1L);
        assertThat(reloaded.getImageUrl()).isEqualTo("/uploads/club-posts/new-uuid.jpg");
        assertThat(reloaded.getName()).isEqualTo("Chess Club");
    }
}

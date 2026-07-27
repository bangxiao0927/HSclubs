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
}

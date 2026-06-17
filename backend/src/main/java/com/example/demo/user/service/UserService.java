package com.example.demo.user.service;

import java.time.Year;
import java.util.List;

import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.model.Club;
import com.example.demo.club.model.ClubMemberView;
import com.example.demo.club.model.ClubMembershipRequest;
import com.example.demo.user.mapper.UserProfileMapper;
import com.example.demo.user.model.UserProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    private static final int HIGH_SCHOOL_DURATION_YEARS = 4;

    private final OAuthUserMapper oAuthUserMapper;
    private final UserProfileMapper userProfileMapper;
    private final ClubMapper clubMapper;

    public UserService(OAuthUserMapper oAuthUserMapper,
                       UserProfileMapper userProfileMapper,
                       ClubMapper clubMapper) {
        this.oAuthUserMapper = oAuthUserMapper;
        this.userProfileMapper = userProfileMapper;
        this.clubMapper = clubMapper;
    }

    public Integer findGraduationYearByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        Long userId = oAuthUserMapper.findIdByEmail(email);
        if (userId == null) {
            return null;
        }
        UserProfile profile = userProfileMapper.findByOauthUserId(userId);
        return profile != null ? profile.getGraduationYear() : null;
    }

    @Transactional
    public void updateGraduationYear(String email, Integer graduationYear) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Authenticated email is required");
        }
        validateGraduationYear(graduationYear);
        Long userId = oAuthUserMapper.findIdByEmail(email);
        if (userId == null) {
            throw new IllegalStateException("User record was not found for the provided email");
        }

        UserProfile profile = new UserProfile();
        profile.setOauthUserId(userId);
        profile.setGraduationYear(graduationYear);
        userProfileMapper.upsert(profile);
    }

    public List<Integer> getCurrentHighSchoolGraduationYears() {
        int currentYear = Year.now().getValue();
        return java.util.stream.IntStream.range(0, HIGH_SCHOOL_DURATION_YEARS)
            .map(i -> currentYear + i)
            .boxed()
            .toList();
    }

    public List<Club> findUserClubs(String email) {
        Long userId = requireUserId(email);
        return clubMapper.findClubsByOauthUserId(userId);
    }

    public List<ClubMembershipRequest> findUserPendingRequests(String email) {
        Long userId = requireUserId(email);
        return clubMapper.findPendingRequestsByOauthUserId(userId);
    }

    private Long requireUserId(String email) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Email is required");
        }
        Long userId = oAuthUserMapper.findIdByEmail(email);
        if (userId == null) {
            throw new IllegalStateException("User not found");
        }
        return userId;
    }

    private void validateGraduationYear(Integer graduationYear) {
        if (graduationYear == null) {
            throw new IllegalArgumentException("Graduation year is required");
        }
        List<Integer> allowedYears = getCurrentHighSchoolGraduationYears();
        if (!allowedYears.contains(graduationYear)) {
            throw new IllegalArgumentException("Graduation year must be within the current four-year high school range");
        }
    }
}

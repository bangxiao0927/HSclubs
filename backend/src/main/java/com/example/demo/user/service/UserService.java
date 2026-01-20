package com.example.demo.user.service;

import java.time.Year;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.user.mapper.UserProfileMapper;
import com.example.demo.user.model.UserProfile;

@Service
public class UserService {

    private static final int HIGH_SCHOOL_DURATION_YEARS = 4;

    private final OAuthUserMapper oAuthUserMapper;
    private final UserProfileMapper userProfileMapper;

    public UserService(OAuthUserMapper oAuthUserMapper, UserProfileMapper userProfileMapper) {
        this.oAuthUserMapper = oAuthUserMapper;
        this.userProfileMapper = userProfileMapper;
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

    private void validateGraduationYear(Integer graduationYear) {
        if (graduationYear == null) {
            throw new IllegalArgumentException("Graduation year is required");
        }
        List<Integer> allowedYears = getCurrentHighSchoolGraduationYears();
        boolean isAllowed = allowedYears.contains(graduationYear);
        if (!isAllowed) {
            throw new IllegalArgumentException("Graduation year must be within the current four-year high school range");
        }
    }
}

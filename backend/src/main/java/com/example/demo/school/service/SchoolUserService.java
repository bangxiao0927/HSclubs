package com.example.demo.school.service;

import com.example.demo.school.mapper.SchoolUserMapper;
import com.example.demo.school.model.SchoolUser;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SchoolUserService {

    private final SchoolUserMapper schoolUserMapper;

    public SchoolUserService(SchoolUserMapper schoolUserMapper) {
        this.schoolUserMapper = schoolUserMapper;
    }

    public List<SchoolUser> findActiveByOauthUserId(Long oauthUserId) {
        return schoolUserMapper.findByOauthUserId(oauthUserId);
    }

    public SchoolUser findBySchoolAndUser(Long schoolId, Long oauthUserId) {
        return schoolUserMapper.findBySchoolAndUser(schoolId, oauthUserId);
    }

    public boolean isSchoolAdmin(Long schoolId, Long oauthUserId) {
        SchoolUser membership = findBySchoolAndUser(schoolId, oauthUserId);
        return membership != null
            && "active".equalsIgnoreCase(membership.getStatus())
            && "school_admin".equalsIgnoreCase(membership.getRole());
    }
}

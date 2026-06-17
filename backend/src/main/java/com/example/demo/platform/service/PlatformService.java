package com.example.demo.platform.service;

import com.example.demo.school.mapper.SchoolMapper;
import com.example.demo.school.model.School;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PlatformService {

    private final SchoolMapper schoolMapper;

    public PlatformService(SchoolMapper schoolMapper) {
        this.schoolMapper = schoolMapper;
    }

    public List<School> findAll() {
        return schoolMapper.findAllActive();
    }

    public School createSchool(School school) {
        if (!StringUtils.hasText(school.getSlug())) {
            throw new IllegalArgumentException("School slug is required");
        }
        if (!StringUtils.hasText(school.getSchoolName())) {
            throw new IllegalArgumentException("School name is required");
        }
        school.setId(null);
        if (!StringUtils.hasText(school.getStatus())) {
            school.setStatus("active");
        }
        if (!StringUtils.hasText(school.getTimezone())) {
            school.setTimezone("America/Los_Angeles");
        }
        schoolMapper.insert(school);
        return school;
    }

    public School updateSchool(String slug, School school) {
        School existing = schoolMapper.findBySlug(slug);
        if (existing == null) {
            throw new IllegalArgumentException("School not found: " + slug);
        }
        school.setSlug(slug);
        schoolMapper.update(school);
        School updated = schoolMapper.findBySlug(slug);
        return updated != null ? updated : school;
    }
}

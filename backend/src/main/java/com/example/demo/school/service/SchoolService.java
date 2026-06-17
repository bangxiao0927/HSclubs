package com.example.demo.school.service;

import com.example.demo.school.mapper.SchoolMapper;
import com.example.demo.school.model.School;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SchoolService {

    private final SchoolMapper schoolMapper;

    public SchoolService(SchoolMapper schoolMapper) {
        this.schoolMapper = schoolMapper;
    }

    public List<School> findAllActive() {
        return schoolMapper.findAllActive();
    }

    public School findBySlug(String slug) {
        return schoolMapper.findBySlug(slug);
    }

    public School findById(Long id) {
        return schoolMapper.findById(id);
    }
}

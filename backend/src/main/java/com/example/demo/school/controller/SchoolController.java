package com.example.demo.school.controller;

import com.example.demo.school.model.School;
import com.example.demo.school.service.SchoolService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/schools")
public class SchoolController {

    private final SchoolService schoolService;

    public SchoolController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @GetMapping
    public List<School> list() {
        return schoolService.findAllActive();
    }

    @GetMapping("/{slug}")
    public School get(@PathVariable String slug) {
        School school = schoolService.findBySlug(slug);
        if (school == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "School not found");
        }
        return school;
    }
}

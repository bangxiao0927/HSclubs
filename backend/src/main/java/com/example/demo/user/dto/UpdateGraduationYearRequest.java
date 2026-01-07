package com.example.demo.user.dto;

import jakarta.validation.constraints.NotNull;

public class UpdateGraduationYearRequest {

    @NotNull(message = "Graduation year is required")
    private Integer graduationYear;

    public Integer getGraduationYear() {
        return graduationYear;
    }

    public void setGraduationYear(Integer graduationYear) {
        this.graduationYear = graduationYear;
    }
}

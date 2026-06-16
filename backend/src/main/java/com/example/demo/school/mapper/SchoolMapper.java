package com.example.demo.school.mapper;

import com.example.demo.school.model.School;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SchoolMapper {

    School findById(@Param("id") Long id);

    School findBySlug(@Param("slug") String slug);

    List<School> findAllActive();

    int insert(School school);

    int update(School school);
}

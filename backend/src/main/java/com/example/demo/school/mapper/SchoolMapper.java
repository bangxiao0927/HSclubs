package com.example.demo.school.mapper;

import com.example.demo.school.model.School;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SchoolMapper {

    School findById(@Param("id") Long id);
}

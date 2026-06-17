package com.example.demo.school.mapper;

import com.example.demo.school.model.SchoolAdminInvitation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SchoolAdminInvitationMapper {

    int insert(SchoolAdminInvitation invitation);

    SchoolAdminInvitation findByToken(@Param("token") String token);

    int updateStatus(@Param("id") Long id,
                     @Param("status") String status);

    SchoolAdminInvitation findBySchoolAndEmail(@Param("schoolId") Long schoolId,
                                               @Param("email") String email);
}

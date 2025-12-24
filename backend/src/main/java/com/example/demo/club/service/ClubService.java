package com.example.demo.club.service;

import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.model.Club;
import com.example.demo.school.mapper.SchoolMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClubService {

    private final ClubMapper clubMapper;
    private final SchoolMapper schoolMapper;

    public ClubService(ClubMapper clubMapper, SchoolMapper schoolMapper) {
        this.clubMapper = clubMapper;
        this.schoolMapper = schoolMapper;
    }

    public List<Club> findAll() {
        return clubMapper.findAll();
    }

    public Club findById(Long id) {
        return clubMapper.findById(id);
    }

    public Club create(Club club) {
        ensureSchoolExists(club.getSchoolId());
        club.setId(null);
        clubMapper.insert(club);
        return club;
    }

    public Club update(Long id, Club club) {
        ensureSchoolExists(club.getSchoolId());
        club.setId(id);
        clubMapper.update(club);
        return club;
    }

    public void delete(Long id) {
        clubMapper.delete(id);
    }

    private void ensureSchoolExists(Long schoolId) {
        if (schoolId == null || schoolMapper.findById(schoolId) == null) {
            throw new IllegalArgumentException("Invalid schoolId");
        }
    }
}

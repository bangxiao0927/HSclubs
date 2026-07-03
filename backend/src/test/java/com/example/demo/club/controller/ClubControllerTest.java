package com.example.demo.club.controller;

import com.example.demo.club.model.Club;
import com.example.demo.club.service.ClubService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class ClubControllerTest {

    @Mock
    private ClubService clubService;

    @InjectMocks
    private ClubController clubController;

    private MockMvc mockMvc;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(clubController).build();
    }

    @Test
    public void testSearchByName() throws Exception {
        // Arrange
        Club club = new Club();
        club.setId(1L);
        club.setName("Math Club");

        when(clubService.searchClubs("Math", null, null, null)).thenReturn(List.of(club));

        // Act & Assert
        mockMvc.perform(get("/api/clubs")
                .param("name", "Math"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Math Club"));
    }

    @Test
    public void testSearchByCategory() throws Exception {
        // Arrange
        Club club = new Club();
        club.setId(2L);
        club.setName("Science Club");
        club.setCategory("Science");

        when(clubService.searchClubs(null, "Science", null, null)).thenReturn(List.of(club));

        // Act & Assert
        mockMvc.perform(get("/api/clubs")
                .param("category", "Science"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Science Club"));
    }

    @Test
    public void testSearchByAlias() throws Exception {
        // Arrange
        Club club = new Club();
        club.setId(3L);
        club.setName("Art Club");
        club.setAliasName("Visual Arts");

        when(clubService.searchClubs(null, null, "Visual Arts", null)).thenReturn(List.of(club));

        // Act & Assert
        mockMvc.perform(get("/api/clubs")
                .param("alias", "Visual Arts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Art Club"));
    }

    @Test
    public void testSearchByAdvisor() throws Exception {
        // Arrange
        Club club = new Club();
        club.setId(4L);
        club.setName("Music Club");
        club.setAdvisor("Mr. Smith");

        when(clubService.searchClubs(null, null, null, "Mr. Smith")).thenReturn(List.of(club));

        // Act & Assert
        mockMvc.perform(get("/api/clubs")
                .param("advisor", "Mr. Smith"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Music Club"));
    }

    @Test
    public void testSearchMultipleParameters() throws Exception {
        // Arrange
        Club club1 = new Club();
        club1.setId(5L);
        club1.setName("Chess Club");
        club1.setCategory("Games");

        Club club2 = new Club();
        club2.setId(6L);
        club2.setName("Checkers Club");
        club2.setCategory("Games");

        when(clubService.searchClubs("Chess", "Games", null, null)).thenReturn(List.of(club1));

        // Act & Assert
        mockMvc.perform(get("/api/clubs")
                .param("name", "Chess")
                .param("category", "Games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Chess Club"));
    }
}

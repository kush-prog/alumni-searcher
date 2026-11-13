package com.alumni.searcher.service;

import com.alumni.searcher.dto.AlumniProfileResponse;
import com.alumni.searcher.dto.AlumniSearchRequest;
import com.alumni.searcher.entity.Alumni;
import com.alumni.searcher.phantombuster.PhantomBusterClient;
import com.alumni.searcher.phantombuster.ScrapedProfile;
import com.alumni.searcher.repository.AlumniRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlumniServiceTest {

    private static final String JOHN_URL = "https://www.linkedin.com/in/johndoe";
    private static final String JANE_URL = "https://www.linkedin.com/in/janesmith";

    @Mock
    private PhantomBusterClient phantomBusterClient;

    @Mock
    private AlumniRepository alumniRepository;

    @InjectMocks
    private AlumniService alumniService;

    @BeforeEach
    void saveAllReturnsWhatItWasGiven() {
        lenient().when(alumniRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void searchesWithDesignationUniversityAndYear() {
        when(phantomBusterClient.searchProfiles(any())).thenReturn(List.of());

        alumniService.searchAlumni(new AlumniSearchRequest("  University of XYZ ", " Software Engineer ", 2020));

        verify(phantomBusterClient).searchProfiles("\"Software Engineer\" \"University of XYZ\" 2020");
    }

    @Test
    void leavesYearOutOfQueryWhenNotGiven() {
        when(phantomBusterClient.searchProfiles(any())).thenReturn(List.of());

        alumniService.searchAlumni(new AlumniSearchRequest("University of XYZ", "Data Scientist", null));

        verify(phantomBusterClient).searchProfiles("\"Data Scientist\" \"University of XYZ\"");
    }

    @Test
    void savesNewProfilesAndReturnsThem() {
        when(phantomBusterClient.searchProfiles(any())).thenReturn(List.of(
                new ScrapedProfile("John Doe", "Passionate Software Engineer at XYZ Corp", "Software Engineer",
                        "New York, NY", null, JOHN_URL)));
        when(alumniRepository.findByProfileUrlIn(anyCollection())).thenReturn(List.of());

        List<AlumniProfileResponse> result =
                alumniService.searchAlumni(new AlumniSearchRequest("University of XYZ", "Software Engineer", 2020));

        assertThat(result).containsExactly(new AlumniProfileResponse("John Doe", "Software Engineer",
                "University of XYZ", "New York, NY", "Passionate Software Engineer at XYZ Corp", 2020, JOHN_URL));
    }

    @Test
    void fallsBackToSearchedValuesWhenProfileLacksRoleOrSchool() {
        when(phantomBusterClient.searchProfiles(any())).thenReturn(List.of(
                new ScrapedProfile("Jane Smith", "Data Scientist | AI Enthusiast", null,
                        "San Francisco, CA", null, JANE_URL)));
        when(alumniRepository.findByProfileUrlIn(anyCollection())).thenReturn(List.of());

        AlumniProfileResponse jane =
                alumniService.searchAlumni(new AlumniSearchRequest("University of XYZ", "Data Scientist", null)).getFirst();

        assertThat(jane.currentRole()).isEqualTo("Data Scientist");
        assertThat(jane.university()).isEqualTo("University of XYZ");
        assertThat(jane.passoutYear()).isNull();
    }

    @Test
    void updatesAlreadySavedProfileInsteadOfDuplicatingIt() {
        Alumni existing = new Alumni();
        existing.setName("John Doe");
        existing.setProfileUrl(JOHN_URL);
        existing.setLocation("Boston, MA");
        existing.setPassoutYear(2020);

        when(phantomBusterClient.searchProfiles(any())).thenReturn(List.of(
                new ScrapedProfile("John Doe", "Senior Engineer", "Senior Engineer", "New York, NY", null, JOHN_URL)));
        when(alumniRepository.findByProfileUrlIn(anyCollection())).thenReturn(List.of(existing));

        alumniService.searchAlumni(new AlumniSearchRequest("University of XYZ", "Engineer", null));

        List<Alumni> saved = capturedSave();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst()).isSameAs(existing);
        assertThat(existing.getLocation()).isEqualTo("New York, NY");
        assertThat(existing.getPassoutYear()).as("a search without a year keeps the known year").isEqualTo(2020);
    }

    @Test
    void skipsIncompleteAndDuplicateProfiles() {
        when(phantomBusterClient.searchProfiles(any())).thenReturn(List.of(
                new ScrapedProfile("John Doe", null, null, null, null, JOHN_URL),
                new ScrapedProfile("John Doe", null, null, null, null, JOHN_URL),
                new ScrapedProfile(null, "No name", null, null, null, JANE_URL),
                new ScrapedProfile("No Link", null, null, null, null, null)));
        when(alumniRepository.findByProfileUrlIn(anyCollection())).thenReturn(List.of());

        List<AlumniProfileResponse> result =
                alumniService.searchAlumni(new AlumniSearchRequest("University of XYZ", "Engineer", null));

        assertThat(result).extracting(AlumniProfileResponse::profileUrl).containsExactly(JOHN_URL);
    }

    @Test
    void doesNotTouchDatabaseWhenNothingIsFound() {
        when(phantomBusterClient.searchProfiles(any())).thenReturn(List.of());

        List<AlumniProfileResponse> result =
                alumniService.searchAlumni(new AlumniSearchRequest("University of XYZ", "Engineer", null));

        assertThat(result).isEmpty();
        verify(alumniRepository, never()).saveAll(anyList());
    }

    @Test
    void listsAllSavedAlumniMostRecentlyUpdatedFirst() {
        Alumni john = new Alumni();
        john.setName("John Doe");
        john.setCurrentRole("Software Engineer");
        john.setUniversity("University of XYZ");
        john.setPassoutYear(2020);
        john.setProfileUrl(JOHN_URL);
        Alumni jane = new Alumni();
        jane.setName("Jane Smith");
        jane.setUniversity("University of XYZ");
        jane.setProfileUrl(JANE_URL);
        when(alumniRepository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(john, jane));

        List<AlumniProfileResponse> result = alumniService.getAllAlumni();

        assertThat(result).extracting(AlumniProfileResponse::name).containsExactly("John Doe", "Jane Smith");
        assertThat(result.getFirst()).isEqualTo(new AlumniProfileResponse("John Doe", "Software Engineer",
                "University of XYZ", null, null, 2020, JOHN_URL));
        verify(phantomBusterClient, never()).searchProfiles(any());
    }

    @Test
    void listsNothingWhenNoAlumniAreSaved() {
        when(alumniRepository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of());

        assertThat(alumniService.getAllAlumni()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private List<Alumni> capturedSave() {
        ArgumentCaptor<List<Alumni>> captor = ArgumentCaptor.forClass(List.class);
        verify(alumniRepository).saveAll(captor.capture());
        return captor.getValue();
    }
}

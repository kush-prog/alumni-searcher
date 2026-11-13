package com.alumni.searcher.repository;

import com.alumni.searcher.entity.Alumni;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against an in-memory database to check the table mapping,
 * the unique LinkedIn URL rule and the custom queries.
 */
@DataJpaTest
class AlumniRepositoryTest {

    @Autowired
    private AlumniRepository alumniRepository;

    @Test
    void savesAlumniAndFillsTimestamps() {
        Alumni saved = alumniRepository.saveAndFlush(alumni("John Doe", "https://www.linkedin.com/in/johndoe"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(alumniRepository.findById(saved.getId()))
                .get()
                .satisfies(found -> {
                    assertThat(found.getCurrentRole()).isEqualTo("Software Engineer");
                    assertThat(found.getPassoutYear()).isEqualTo(2020);
                });
    }

    @Test
    void findsAlumniByTheirLinkedinUrls() {
        alumniRepository.saveAll(List.of(
                alumni("John Doe", "https://www.linkedin.com/in/johndoe"),
                alumni("Jane Smith", "https://www.linkedin.com/in/janesmith"),
                alumni("Someone Else", "https://www.linkedin.com/in/someoneelse")));

        List<Alumni> found = alumniRepository.findByProfileUrlIn(List.of(
                "https://www.linkedin.com/in/johndoe",
                "https://www.linkedin.com/in/janesmith",
                "https://www.linkedin.com/in/unknown"));

        assertThat(found).extracting(Alumni::getName).containsExactlyInAnyOrder("John Doe", "Jane Smith");
    }

    @Test
    void refusesTwoRowsWithTheSameLinkedinUrl() {
        alumniRepository.saveAndFlush(alumni("John Doe", "https://www.linkedin.com/in/johndoe"));

        assertThatThrownBy(() ->
                alumniRepository.saveAndFlush(alumni("John Again", "https://www.linkedin.com/in/johndoe")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listsMostRecentlyUpdatedFirst() throws InterruptedException {
        Alumni older = alumniRepository.saveAndFlush(alumni("Older", "https://www.linkedin.com/in/older"));
        Thread.sleep(5);
        alumniRepository.saveAndFlush(alumni("Newer", "https://www.linkedin.com/in/newer"));
        Thread.sleep(5);
        older.setLocation("Pune, India");
        alumniRepository.saveAndFlush(older);

        assertThat(alumniRepository.findAllByOrderByUpdatedAtDesc())
                .extracting(Alumni::getName)
                .containsExactly("Older", "Newer");
    }

    private static Alumni alumni(String name, String profileUrl) {
        Alumni alumni = new Alumni();
        alumni.setName(name);
        alumni.setCurrentRole("Software Engineer");
        alumni.setUniversity("University of XYZ");
        alumni.setPassoutYear(2020);
        alumni.setProfileUrl(profileUrl);
        return alumni;
    }
}

package com.alumni.searcher.service;

import com.alumni.searcher.dto.AlumniProfileResponse;
import com.alumni.searcher.dto.AlumniSearchRequest;
import com.alumni.searcher.entity.Alumni;
import com.alumni.searcher.phantombuster.PhantomBusterClient;
import com.alumni.searcher.phantombuster.ScrapedProfile;
import com.alumni.searcher.repository.AlumniRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AlumniService {

    private final PhantomBusterClient phantomBusterClient;
    private final AlumniRepository alumniRepository;

    public AlumniService(PhantomBusterClient phantomBusterClient, AlumniRepository alumniRepository) {
        this.phantomBusterClient = phantomBusterClient;
        this.alumniRepository = alumniRepository;
    }

    // Searches LinkedIn for people from the given university who hold the given role, 
    // saves what was found and returns it. Profiles we have seen before are updated
    // instead of being stored twice.
    public List<AlumniProfileResponse> searchAlumni(AlumniSearchRequest request) {
        String university = request.university().trim();
        String designation = request.designation().trim();

        List<ScrapedProfile> scrapedProfiles =
                phantomBusterClient.searchProfiles(buildSearchQuery(university, designation, request.passoutYear()));

        Map<String, ScrapedProfile> usableProfiles = keepUsableProfiles(scrapedProfiles);
        if (usableProfiles.isEmpty()) {
            return List.of();
        }

        Map<String, Alumni> alreadySaved = alumniRepository.findByProfileUrlIn(usableProfiles.keySet()).stream()
                .collect(Collectors.toMap(Alumni::getProfileUrl, Function.identity()));

        List<Alumni> toSave = usableProfiles.values().stream()
                .map(profile -> {
                    Alumni alumni = alreadySaved.getOrDefault(profile.profileUrl(), new Alumni());
                    fillDetails(alumni, profile, university, designation, request.passoutYear());
                    return alumni;
                })
                .toList();

        return alumniRepository.saveAll(toSave).stream()
                .map(AlumniProfileResponse::from)
                .toList();
    }

    // Returns every alumni profile saved by earlier searches, most recently updated first.
    // This only reads the database; it never calls PhantomBuster.
    @Transactional(readOnly = true)
    public List<AlumniProfileResponse> getAllAlumni() {
        return alumniRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(AlumniProfileResponse::from)
                .toList();
    }

    String buildSearchQuery(String university, String designation, Integer passoutYear) {
        String query = "\"" + designation + "\" \"" + university + "\"";
        return passoutYear == null ? query : query + " " + passoutYear;
    }

    // A profile without a name or a LinkedIn link is not useful to store,
    // and the same person can show up twice in one result, so keep one copy.
    private Map<String, ScrapedProfile> keepUsableProfiles(List<ScrapedProfile> profiles) {
        Map<String, ScrapedProfile> byUrl = new LinkedHashMap<>();
        for (ScrapedProfile profile : profiles) {
            if (profile.fullName() != null && profile.profileUrl() != null) {
                byUrl.putIfAbsent(profile.profileUrl(), profile);
            }
        }
        return byUrl;
    }

    private void fillDetails(Alumni alumni, ScrapedProfile profile,
                             String university, String designation, Integer passoutYear) {
        alumni.setName(profile.fullName());
        alumni.setProfileUrl(profile.profileUrl());
        alumni.setCurrentRole(profile.jobTitle() != null ? profile.jobTitle() : designation);
        alumni.setUniversity(profile.school() != null ? profile.school() : university);
        alumni.setLocation(profile.location());
        alumni.setLinkedinHeadline(profile.headline());
        if (passoutYear != null) {
            alumni.setPassoutYear(passoutYear);
        }
    }
}

package com.alumni.searcher.dto;

import com.alumni.searcher.entity.Alumni;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One alumni profile")
public record AlumniProfileResponse(
        @Schema(example = "John Doe") String name,
        @Schema(example = "Software Engineer") String currentRole,
        @Schema(example = "Stanford University") String university,
        @Schema(example = "New York, NY") String location,
        @Schema(example = "Passionate Software Engineer at XYZ Corp") String linkedinHeadline,
        @Schema(example = "2020") Integer passoutYear,
        @Schema(example = "https://www.linkedin.com/in/johndoe") String profileUrl
) {

    public static AlumniProfileResponse from(Alumni alumni) {
        return new AlumniProfileResponse(
                alumni.getName(),
                alumni.getCurrentRole(),
                alumni.getUniversity(),
                alumni.getLocation(),
                alumni.getLinkedinHeadline(),
                alumni.getPassoutYear(),
                alumni.getProfileUrl()
        );
    }
}

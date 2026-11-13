package com.alumni.searcher.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "What to search for")
public record AlumniSearchRequest(

        @Schema(description = "University the person studied at", example = "Stanford University",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200)
        @NotBlank(message = "University name is required")
        @Size(max = 200, message = "University name must be at most 200 characters")
        String university,

        @Schema(description = "Current job title", example = "Software Engineer",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 150)
        @NotBlank(message = "Designation is required")
        @Size(max = 150, message = "Designation must be at most 150 characters")
        String designation,

        @Schema(description = "Optional pass-out year. A number or a number in quotes.", example = "2020",
                minimum = "1950", maximum = "2100", nullable = true)
        @Min(value = 1950, message = "Pass-out year must be 1950 or later")
        @Max(value = 2100, message = "Pass-out year must be 2100 or earlier")
        Integer passoutYear
) {
}

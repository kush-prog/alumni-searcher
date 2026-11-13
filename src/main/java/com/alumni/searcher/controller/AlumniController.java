package com.alumni.searcher.controller;

import com.alumni.searcher.config.OpenApiConfig;
import com.alumni.searcher.dto.AlumniProfileResponse;
import com.alumni.searcher.dto.AlumniSearchRequest;
import com.alumni.searcher.dto.ApiResponse;
import com.alumni.searcher.service.AlumniService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alumni")
@Tag(name = OpenApiConfig.ALUMNI_TAG)
public class AlumniController {

    private final AlumniService alumniService;

    public AlumniController(AlumniService alumniService) {
        this.alumniService = alumniService;
    }

    @Operation(summary = "Search alumni profiles",
            description = "Runs a LinkedIn search through PhantomBuster for people from the given university "
                    + "who hold the given role, saves the profiles found and returns them. "
                    + "Profiles seen before are updated, not duplicated. "
                    + "The call waits for PhantomBuster, which can take up to 3 minutes.")
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<List<AlumniProfileResponse>>> searchAlumni(
            @Valid @RequestBody AlumniSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(alumniService.searchAlumni(request)));
    }

    @Operation(summary = "Get all saved alumni profiles",
            description = "Returns every profile saved by earlier searches, most recently updated first. "
                    + "Reads the database only; PhantomBuster is not called.")
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<AlumniProfileResponse>>> getAllAlumni() {
        return ResponseEntity.ok(ApiResponse.success(alumniService.getAllAlumni()));
    }
}

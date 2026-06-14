package com.fantasy.bff.controller;

import com.fantasy.bff.dto.response.VersionsResponse;
import com.fantasy.bff.service.VersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/versions")
@Tag(name = "Versions", description = "Deployed version and reachability of every service")
public class VersionController {

    private final VersionService versionService;

    public VersionController(VersionService versionService) {
        this.versionService = versionService;
    }

    @GetMapping
    @Operation(summary = "Get service versions",
            description = "Returns the deployed version and reachability of the BFF and each downstream service")
    @ApiResponse(responseCode = "200", description = "Versions retrieved successfully")
    public ResponseEntity<VersionsResponse> getVersions() {
        return ResponseEntity.ok(versionService.getVersions());
    }
}

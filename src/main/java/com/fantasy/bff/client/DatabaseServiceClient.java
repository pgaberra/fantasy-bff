package com.fantasy.bff.client;

import com.fantasy.bff.generated.db.model.CreateProjectionRequest;
import com.fantasy.bff.generated.db.model.ProjectionResponse;
import com.fantasy.bff.generated.db.model.ProjectionSummaryResponse;
import com.fantasy.bff.generated.db.model.UpdateProjectionRequest;
import com.fantasy.bff.model.downstream.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DatabaseServiceClient {

    Optional<User> findUserByEmail(String email);

    User createUser(String email, String passwordHash);

    boolean existsByEmail(String email);

    List<ProjectionSummaryResponse> listProjections(UUID userId);

    ProjectionResponse getProjection(UUID userId, UUID projectionId);

    ProjectionResponse createProjection(UUID userId, CreateProjectionRequest request);

    ProjectionResponse updateProjection(UUID userId, UUID projectionId, UpdateProjectionRequest request);

    void deleteProjection(UUID userId, UUID projectionId);
}

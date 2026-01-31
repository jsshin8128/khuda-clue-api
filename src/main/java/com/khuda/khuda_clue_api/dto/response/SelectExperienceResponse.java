package com.khuda.khuda_clue_api.dto.response;

import com.khuda.khuda_clue_api.domain.ApplicationStatus;

public record SelectExperienceResponse(
        Long applicationId,
        ApplicationStatus status,
        SelectedExperience selectedExperience
) {
}
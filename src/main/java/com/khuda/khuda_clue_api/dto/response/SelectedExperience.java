package com.khuda.khuda_clue_api.dto.response;

public record SelectedExperience(
        Long experienceId,
        String title,
        Integer startIdx,
        Integer endIdx,
        Double rankScore
) {
}
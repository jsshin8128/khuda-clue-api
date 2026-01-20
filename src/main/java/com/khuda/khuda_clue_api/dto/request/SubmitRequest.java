package com.khuda.khuda_clue_api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SubmitRequest(
        @NotBlank(message = "지원자 ID는 필수입니다.")
        String applicantId,

        @NotBlank(message = "자기소개서 텍스트는 필수입니다.")
        String coverLetterText
) {
}
package com.khuda.khuda_clue_api.dto.response;

import java.util.List;

public record RecommendInterviewQuestionsResponse(
        Long applicationId,
        List<String> interviewRecommendations
) {}

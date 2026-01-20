package com.khuda.khuda_clue_api.controller;

import com.khuda.khuda_clue_api.dto.request.SubmitRequest;
import com.khuda.khuda_clue_api.dto.response.SubmitResponse;
import com.khuda.khuda_clue_api.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    public ResponseEntity<SubmitResponse> submitApplication(@Valid @RequestBody SubmitRequest request) {
        SubmitResponse response = applicationService.createApplication(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
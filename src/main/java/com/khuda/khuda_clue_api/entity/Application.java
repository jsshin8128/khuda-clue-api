package com.khuda.khuda_clue_api.entity;

import com.khuda.khuda_clue_api.domain.ApplicationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "application")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "applicant_id", nullable = false, length = 64)
    private String applicantId;

    @Column(name = "cover_letter_text", nullable = false, columnDefinition = "LONGTEXT")
    private String coverLetterText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ApplicationStatus status;

    @Column(name = "interview_recommendations_json", columnDefinition = "JSON")
    private String interviewRecommendationsJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Application(String applicantId, String coverLetterText) {
        this.applicantId = applicantId;
        this.coverLetterText = coverLetterText;
        this.status = ApplicationStatus.SUBMITTED;
    }

    public void updateStatus(ApplicationStatus status) {
        this.status = status;
    }

    public void updateInterviewRecommendations(String json) {
        this.interviewRecommendationsJson = json;
    }
}
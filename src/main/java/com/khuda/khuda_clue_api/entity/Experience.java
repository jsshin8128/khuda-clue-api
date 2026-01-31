package com.khuda.khuda_clue_api.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "experience")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Experience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "start_idx", nullable = false)
    private Integer startIdx;

    @Column(name = "end_idx", nullable = false)
    private Integer endIdx;

    @Column(name = "rank_score", nullable = false)
    private Double rankScore;

    @Column(name = "is_selected", nullable = false)
    private Boolean isSelected;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Experience(Long applicationId, String title, Integer startIdx, Integer endIdx, Double rankScore, Boolean isSelected) {
        this.applicationId = applicationId;
        this.title = title;
        this.startIdx = startIdx;
        this.endIdx = endIdx;
        this.rankScore = rankScore;
        this.isSelected = isSelected;
    }

    public static Experience createCandidate(Long applicationId, String title, Integer startIdx, Integer endIdx, Double rankScore) {
        return new Experience(applicationId, title, startIdx, endIdx, rankScore, false);
    }

    public static Experience createSelected(Long applicationId, String title, Integer startIdx, Integer endIdx, Double rankScore) {
        return new Experience(applicationId, title, startIdx, endIdx, rankScore, true);
    }

    public void markAsSelected() {
        this.isSelected = true;
    }
}
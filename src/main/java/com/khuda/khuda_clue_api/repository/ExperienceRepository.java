package com.khuda.khuda_clue_api.repository;

import com.khuda.khuda_clue_api.entity.Experience;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExperienceRepository extends JpaRepository<Experience, Long> {

    List<Experience> findByApplicationIdOrderByRankScoreDesc(Long applicationId);

    Optional<Experience> findByApplicationIdAndIsSelectedTrue(Long applicationId);

    boolean existsByApplicationIdAndIsSelectedTrue(Long applicationId);
}
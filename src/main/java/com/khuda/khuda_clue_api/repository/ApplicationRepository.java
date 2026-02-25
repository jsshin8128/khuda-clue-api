package com.khuda.khuda_clue_api.repository;

import com.khuda.khuda_clue_api.domain.ApplicationStatus;
import com.khuda.khuda_clue_api.entity.Application;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    // status 필터 + id 기반 커서 페이지네이션 (첫 페이지: cursorId 없이)
    List<Application> findByStatusOrderByIdAsc(ApplicationStatus status, Pageable pageable);

    // status 필터 + id > cursorId 조건 커서 페이지네이션
    List<Application> findByStatusAndIdGreaterThanOrderByIdAsc(ApplicationStatus status, Long id, Pageable pageable);
}
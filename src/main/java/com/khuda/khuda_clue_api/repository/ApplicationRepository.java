package com.khuda.khuda_clue_api.repository;

import com.khuda.khuda_clue_api.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {
}
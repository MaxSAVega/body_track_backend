package com.rumi.body_track_backend.repository;

import com.rumi.body_track_backend.model.SkinAnomaly;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface SkinAnomalyRepository extends JpaRepository<SkinAnomaly, Long> {
    List<SkinAnomaly> findByUserEmail(String email);
    Page<SkinAnomaly> findByUserEmail(String email, Pageable pageable);
    List<SkinAnomaly> findByUserEmailAndUpdatedAtAfter(String email, LocalDateTime since);
    Page<SkinAnomaly> findByUserEmailAndUpdatedAtAfter(String email, LocalDateTime since, Pageable pageable);
}

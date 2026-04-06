package com.rumi.body_track_backend.repository;

import com.rumi.body_track_backend.model.SkinAnomaly;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SkinAnomalyRepository extends JpaRepository<SkinAnomaly, Long> {
    List<SkinAnomaly> findByUserEmail(String email);
}

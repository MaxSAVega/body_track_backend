package com.rumi.body_track_backend.repository;

import com.rumi.body_track_backend.model.SkinAnomaly;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SkinAnomalyRepository extends JpaRepository<SkinAnomaly, Long> {

    /**
     * All access is scoped by user id in the query itself.
     *
     * <p>This is the core of the IDOR fix. The previous pattern loaded by primary
     * key and compared the owner in Java, which both fetched another user's row into
     * memory and produced a distinguishable error depending on whether the id
     * existed, letting an attacker enumerate the table. A single scoped query
     * returns "not found" for both cases.
     */
    Optional<SkinAnomaly> findByIdAndUserId(Long id, Long userId);

    List<SkinAnomaly> findByUserId(Long userId);
    Page<SkinAnomaly> findByUserId(Long userId, Pageable pageable);

    List<SkinAnomaly> findByUserIdAndUpdatedAtAfter(Long userId, LocalDateTime since);
    Page<SkinAnomaly> findByUserIdAndUpdatedAtAfter(Long userId, LocalDateTime since, Pageable pageable);

    /**
     * Owner-scoped delete.
     *
     * <p>{@code deleteById} was previously reachable by any authenticated user and
     * deleted purely on the supplied id, so any account could erase anyone's
     * medical history. Returns the number of rows removed so the caller can tell a
     * genuine delete from a no-op without a follow-up existence check.
     */
    @Modifying
    @Query("delete from SkinAnomaly a where a.id = :id and a.user.id = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    long countByUserId(Long userId);
}
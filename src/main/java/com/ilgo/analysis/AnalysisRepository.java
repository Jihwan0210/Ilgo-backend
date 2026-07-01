package com.ilgo.analysis;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {

    /** 보관함 목록 첫 페이지: created_at DESC, id DESC 로 정렬. */
    @Query("select a from Analysis a "
            + "where a.user.id = :userId and (:savedOnly = false or a.isSaved = true) "
            + "order by a.createdAt desc, a.id desc")
    List<Analysis> findFirstPage(@Param("userId") UUID userId,
                                 @Param("savedOnly") boolean savedOnly,
                                 Pageable pageable);

    /** 보관함 목록 다음 페이지: (created_at, id) 커서보다 이전 것들. */
    @Query("select a from Analysis a "
            + "where a.user.id = :userId and (:savedOnly = false or a.isSaved = true) "
            + "and (a.createdAt < :cursorCreatedAt "
            + "     or (a.createdAt = :cursorCreatedAt and a.id < :cursorId)) "
            + "order by a.createdAt desc, a.id desc")
    List<Analysis> findAfterCursor(@Param("userId") UUID userId,
                                   @Param("savedOnly") boolean savedOnly,
                                   @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
                                   @Param("cursorId") UUID cursorId,
                                   Pageable pageable);
}

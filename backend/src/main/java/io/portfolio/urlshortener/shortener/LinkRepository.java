package io.portfolio.urlshortener.shortener;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Optional;

/**
 * Sharded repository — every call MUST run inside
 * {@code ShardRouter.executeRead/executeWrite} keyed by the short code so the
 * routing datasource (Track B) can pick the owning shard.
 */
public interface LinkRepository extends JpaRepository<Link, Long> {

    Optional<Link> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    @Modifying
    void deleteByShortCode(String shortCode);

    Page<Link> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

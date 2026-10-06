package io.portfolio.urlshortener.shortener.repository;

import io.portfolio.urlshortener.shortener.entity.Url;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    @Modifying
    void deleteByShortCode(String shortCode);

    Page<Url> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

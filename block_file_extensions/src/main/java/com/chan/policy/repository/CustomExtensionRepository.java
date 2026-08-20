package com.chan.policy.repository;

import com.chan.policy.domain.CustomExtension;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CustomExtensionRepository extends JpaRepository<CustomExtension, Long> {

    boolean existsByExtension(String extension);

    @Query("SELECT c.extension FROM CustomExtension c WHERE c.extension IN :extensions")
    List<String> findRegisteredExtensionsAmong(@Param("extensions") Collection<String> extensions);
}

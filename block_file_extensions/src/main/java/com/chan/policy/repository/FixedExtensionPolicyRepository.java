package com.chan.policy.repository;

import com.chan.policy.domain.FixedExtensionPolicy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FixedExtensionPolicyRepository extends JpaRepository<FixedExtensionPolicy, String> {

    @Query("SELECT p.extension FROM FixedExtensionPolicy p WHERE p.extension IN :extensions AND p.blocked = true")
    List<String> findBlockedExtensionsAmong(@Param("extensions") Collection<String> extensions);
}

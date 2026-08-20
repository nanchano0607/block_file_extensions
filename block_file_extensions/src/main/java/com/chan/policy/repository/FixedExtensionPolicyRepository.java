package com.chan.policy.repository;

import com.chan.policy.domain.FixedExtensionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FixedExtensionPolicyRepository extends JpaRepository<FixedExtensionPolicy, String> {

    boolean existsByExtensionAndBlockedTrue(String extension);
}

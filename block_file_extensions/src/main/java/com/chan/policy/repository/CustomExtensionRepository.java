package com.chan.policy.repository;

import com.chan.policy.domain.CustomExtension;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomExtensionRepository extends JpaRepository<CustomExtension, Long> {

    boolean existsByExtension(String extension);
}

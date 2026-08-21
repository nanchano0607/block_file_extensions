package com.chan.policy.repository;

import com.chan.policy.domain.ExtensionPolicyHistory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtensionPolicyHistoryRepository extends JpaRepository<ExtensionPolicyHistory, Long> {

    Page<ExtensionPolicyHistory> findAllByOrderByChangedAtDescIdDesc(Pageable pageable);
}

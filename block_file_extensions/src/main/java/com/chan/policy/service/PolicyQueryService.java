package com.chan.policy.service;

import com.chan.policy.domain.FixedExtension;
import com.chan.policy.dto.FixedExtensionResponse;
import com.chan.policy.repository.FixedExtensionPolicyRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyQueryService {

    private final FixedExtensionPolicyRepository fixedExtensionPolicyRepository;

    public List<FixedExtensionResponse> getFixedExtensions() {
        return fixedExtensionPolicyRepository.findAll()
                .stream()
                .sorted(Comparator.comparingInt(policy ->
                        FixedExtension.from(policy.getExtension()).ordinal()))
                .map(FixedExtensionResponse::from)
                .toList();
    }
}

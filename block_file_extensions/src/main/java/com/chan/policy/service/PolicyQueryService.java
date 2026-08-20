package com.chan.policy.service;

import com.chan.policy.domain.FixedExtension;
import com.chan.policy.domain.PolicyConstraints;
import com.chan.policy.dto.CustomExtensionItemResponse;
import com.chan.policy.dto.CustomExtensionListResponse;
import com.chan.policy.dto.FixedExtensionResponse;
import com.chan.policy.repository.CustomExtensionRepository;
import com.chan.policy.repository.FixedExtensionPolicyRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyQueryService {

    private final FixedExtensionPolicyRepository fixedExtensionPolicyRepository;
    private final CustomExtensionRepository customExtensionRepository;

    public List<FixedExtensionResponse> getFixedExtensions() {
        return fixedExtensionPolicyRepository.findAll()
                .stream()
                .sorted(Comparator.comparingInt(policy ->
                        FixedExtension.from(policy.getExtension()).ordinal()))
                .map(FixedExtensionResponse::from)
                .toList();
    }

    public CustomExtensionListResponse getCustomExtensions() {
        List<CustomExtensionItemResponse> items = customExtensionRepository
                .findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(CustomExtensionItemResponse::from)
                .toList();

        return new CustomExtensionListResponse(
                items.size(),
                PolicyConstraints.MAX_CUSTOM_EXTENSION_COUNT,
                items
        );
    }
}

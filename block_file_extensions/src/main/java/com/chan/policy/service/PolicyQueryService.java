package com.chan.policy.service;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.ErrorCode;
import com.chan.policy.domain.ExtensionPolicyHistory;
import com.chan.policy.domain.FixedExtension;
import com.chan.policy.domain.PolicyConstraints;
import com.chan.policy.dto.CustomExtensionItemResponse;
import com.chan.policy.dto.CustomExtensionListResponse;
import com.chan.policy.dto.FixedExtensionResponse;
import com.chan.policy.dto.ExtensionPolicyHistoryItemResponse;
import com.chan.policy.dto.ExtensionPolicyHistoryListResponse;
import com.chan.policy.repository.CustomExtensionRepository;
import com.chan.policy.repository.ExtensionPolicyHistoryRepository;
import com.chan.policy.repository.FixedExtensionPolicyRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyQueryService {

    private static final int MAX_HISTORY_PAGE_SIZE = 100;

    private final FixedExtensionPolicyRepository fixedExtensionPolicyRepository;
    private final CustomExtensionRepository customExtensionRepository;
    private final ExtensionPolicyHistoryRepository extensionPolicyHistoryRepository;

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

    public ExtensionPolicyHistoryListResponse getExtensionPolicyHistory(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_HISTORY_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Page<ExtensionPolicyHistory> historyPage =
                extensionPolicyHistoryRepository.findAllByOrderByChangedAtDescIdDesc(
                        PageRequest.of(page, size)
                );

        return new ExtensionPolicyHistoryListResponse(
                historyPage.getNumber(),
                historyPage.getSize(),
                historyPage.getTotalElements(),
                historyPage.getTotalPages(),
                historyPage.hasPrevious(),
                historyPage.hasNext(),
                historyPage.getContent().stream()
                        .map(ExtensionPolicyHistoryItemResponse::from)
                        .toList()
        );
    }
}

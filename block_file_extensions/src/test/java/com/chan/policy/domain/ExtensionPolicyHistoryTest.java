package com.chan.policy.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtensionPolicyHistoryTest {

    @Test
    void 고정_확장자_차단_여부를_변경_행위로_변환한다() {
        ExtensionPolicyHistory blocked = ExtensionPolicyHistory.fixedPolicyChanged("exe", true);
        ExtensionPolicyHistory allowed = ExtensionPolicyHistory.fixedPolicyChanged("exe", false);

        assertThat(blocked.getPolicyType()).isEqualTo(ExtensionPolicyType.FIXED);
        assertThat(blocked.getExtension()).isEqualTo("exe");
        assertThat(blocked.getAction()).isEqualTo(ExtensionPolicyAction.BLOCK_ON);
        assertThat(allowed.getAction()).isEqualTo(ExtensionPolicyAction.BLOCK_OFF);
    }

    @Test
    void 커스텀_확장자_등록과_삭제_행위를_구분한다() {
        ExtensionPolicyHistory added = ExtensionPolicyHistory.customExtensionAdded("sh");
        ExtensionPolicyHistory deleted = ExtensionPolicyHistory.customExtensionDeleted("sh");

        assertThat(added.getPolicyType()).isEqualTo(ExtensionPolicyType.CUSTOM);
        assertThat(added.getAction()).isEqualTo(ExtensionPolicyAction.ADD);
        assertThat(deleted.getPolicyType()).isEqualTo(ExtensionPolicyType.CUSTOM);
        assertThat(deleted.getAction()).isEqualTo(ExtensionPolicyAction.DELETE);
    }

    @Test
    void 빈_확장자나_20자를_초과한_확장자는_이력으로_만들_수_없다() {
        assertThatThrownBy(() -> ExtensionPolicyHistory.customExtensionAdded(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExtensionPolicyHistory.customExtensionAdded("a".repeat(21)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

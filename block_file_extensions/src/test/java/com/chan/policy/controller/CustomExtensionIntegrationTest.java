package com.chan.policy.controller;

import com.chan.policy.domain.CustomExtension;
import com.chan.policy.domain.ExtensionPolicyAction;
import com.chan.policy.domain.ExtensionPolicyHistory;
import com.chan.policy.domain.ExtensionPolicyType;
import com.chan.policy.domain.PolicyWriteLock;
import com.chan.policy.repository.CustomExtensionRepository;
import com.chan.policy.repository.ExtensionPolicyHistoryRepository;
import com.chan.policy.repository.FixedExtensionPolicyRepository;
import com.chan.policy.repository.PolicyWriteLockRepository;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CustomExtensionIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CustomExtensionRepository customExtensionRepository;

    @Autowired
    ExtensionPolicyHistoryRepository extensionPolicyHistoryRepository;

    @Autowired
    FixedExtensionPolicyRepository fixedExtensionPolicyRepository;

    @Autowired
    PolicyWriteLockRepository policyWriteLockRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearCustomExtensions() {
        extensionPolicyHistoryRepository.deleteAllInBatch();
        customExtensionRepository.deleteAllInBatch();
        var fixedPolicy = fixedExtensionPolicyRepository.findById("exe").orElseThrow();
        fixedPolicy.changeBlocked(false);
        fixedExtensionPolicyRepository.saveAndFlush(fixedPolicy);
    }

    @Test
    void 커스텀_확장자를_정규화해_저장하고_201을_반환한다() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/policy/custom-extensions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"extension":" .Ps1 "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("등록되었습니다."))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.extension").value("ps1"))
                .andReturn();

        CustomExtension saved = customExtensionRepository.findAll().getFirst();
        Number responseId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");

        assertThat(customExtensionRepository.count()).isEqualTo(1);
        assertThat(saved.getId()).isEqualTo(responseId.longValue());
        assertThat(saved.getExtension()).isEqualTo("ps1");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(extensionPolicyHistoryRepository.findAll()).singleElement().satisfies(history -> {
            assertThat(history.getPolicyType()).isEqualTo(ExtensionPolicyType.CUSTOM);
            assertThat(history.getExtension()).isEqualTo("ps1");
            assertThat(history.getAction()).isEqualTo(ExtensionPolicyAction.ADD);
            assertThat(history.getChangedAt()).isNotNull();
        });
    }

    @Test
    void 존재하는_커스텀_확장자를_삭제한다() throws Exception {
        CustomExtension saved = customExtensionRepository.saveAndFlush(new CustomExtension("sh"));

        mockMvc.perform(delete("/api/policy/custom-extensions/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("삭제되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(customExtensionRepository.existsById(saved.getId())).isFalse();
        assertThat(customExtensionRepository.count()).isZero();
        assertThat(extensionPolicyHistoryRepository.findAll()).singleElement().satisfies(history -> {
            assertThat(history.getPolicyType()).isEqualTo(ExtensionPolicyType.CUSTOM);
            assertThat(history.getExtension()).isEqualTo("sh");
            assertThat(history.getAction()).isEqualTo(ExtensionPolicyAction.DELETE);
        });
    }

    @Test
    void 존재하지_않는_커스텀_확장자_삭제는_404를_반환한다() throws Exception {
        mockMvc.perform(delete("/api/policy/custom-extensions/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("존재하지 않는 커스텀 확장자입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(customExtensionRepository.count()).isZero();
        assertThat(extensionPolicyHistoryRepository.count()).isZero();
    }

    @Test
    void 고정_확장자_차단_상태가_바뀌면_변경_이력을_저장한다() throws Exception {
        mockMvc.perform(patch("/api/policy/fixed-extensions/exe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blocked": true}
                                """))
                .andExpect(status().isOk());

        assertThat(extensionPolicyHistoryRepository.findAll()).singleElement().satisfies(history -> {
            assertThat(history.getPolicyType()).isEqualTo(ExtensionPolicyType.FIXED);
            assertThat(history.getExtension()).isEqualTo("exe");
            assertThat(history.getAction()).isEqualTo(ExtensionPolicyAction.BLOCK_ON);
        });
    }

    @Test
    void 고정_확장자_차단_상태가_같으면_변경_이력을_저장하지_않는다() throws Exception {
        mockMvc.perform(patch("/api/policy/fixed-extensions/exe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blocked": false}
                                """))
                .andExpect(status().isOk());

        assertThat(extensionPolicyHistoryRepository.count()).isZero();
    }

    @Test
    void 정책_변경_이력을_최신순으로_페이지_조회한다() throws Exception {
        extensionPolicyHistoryRepository.saveAndFlush(
                ExtensionPolicyHistory.customExtensionAdded("sh")
        );
        extensionPolicyHistoryRepository.saveAndFlush(
                ExtensionPolicyHistory.customExtensionDeleted("sh")
        );

        mockMvc.perform(get("/api/policy/history")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasPrevious").value(false))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].policyType").value("CUSTOM"))
                .andExpect(jsonPath("$.data.items[0].extension").value("sh"))
                .andExpect(jsonPath("$.data.items[0].action").value("DELETE"))
                .andExpect(jsonPath("$.data.items[0].changedAt").isString());
    }

    @Test
    void 정책_변경_이력의_페이지_범위가_올바르지_않으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/policy/history")
                        .param("page", "-1")
                        .param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."));

        mockMvc.perform(get("/api/policy/history")
                        .param("page", "0")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."));
    }

    @Test
    void 길이_규칙을_위반하면_400을_반환하고_저장하지_않는다() throws Exception {
        assertErrorResponse(" ", 400, "확장자는 1~20자로 입력해주세요.");
        assertThat(customExtensionRepository.count()).isZero();
        assertThat(extensionPolicyHistoryRepository.count()).isZero();
    }

    @Test
    void 형식_규칙을_위반하면_400을_반환하고_저장하지_않는다() throws Exception {
        assertErrorResponse("sh!", 400, "영문/숫자만 입력 가능합니다.");
        assertThat(customExtensionRepository.count()).isZero();
        assertThat(extensionPolicyHistoryRepository.count()).isZero();
    }

    @Test
    void 고정_확장자와_중복되면_409를_반환하고_저장하지_않는다() throws Exception {
        assertErrorResponse(" .EXE ", 409, "이미 고정 차단 목록에 있는 확장자입니다.");
        assertThat(customExtensionRepository.count()).isZero();
        assertThat(extensionPolicyHistoryRepository.count()).isZero();
    }

    @Test
    void 기존_커스텀_확장자와_중복되면_409를_반환하고_저장하지_않는다() throws Exception {
        customExtensionRepository.saveAndFlush(new CustomExtension("sh"));

        assertErrorResponse(" SH ", 409, "이미 등록된 확장자입니다.");
        assertThat(customExtensionRepository.count()).isEqualTo(1);
        assertThat(extensionPolicyHistoryRepository.count()).isZero();
    }

    @Test
    void 커스텀_확장자가_200개이면_422를_반환하고_저장하지_않는다() throws Exception {
        customExtensionRepository.saveAllAndFlush(
                IntStream.range(0, 200)
                        .mapToObj(index -> new CustomExtension("x" + index))
                        .toList()
        );

        assertErrorResponse("new", 422, "커스텀 확장자는 최대 200개까지 등록할 수 있습니다.");
        assertThat(customExtensionRepository.count()).isEqualTo(200);
        assertThat(extensionPolicyHistoryRepository.count()).isZero();
    }

    @Test
    void 동시에_두_요청이_들어와도_200개_한도를_넘기지_않는다() throws Exception {
        customExtensionRepository.saveAllAndFlush(
                IntStream.range(0, 199)
                        .mapToObj(index -> new CustomExtension("y" + index))
                        .toList()
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures = List.of(
                    executor.submit(() -> requestStatus(ready, start, "aaa")),
                    executor.submit(() -> requestStatus(ready, start, "bbb"))
            );

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(5, TimeUnit.SECONDS));
            }

            assertThat(statuses).containsExactlyInAnyOrder(201, 422);
        } finally {
            executor.shutdownNow();
        }

        assertThat(customExtensionRepository.count()).isEqualTo(200);
        assertThat(extensionPolicyHistoryRepository.findAll()).singleElement().satisfies(history -> {
            assertThat(history.getPolicyType()).isEqualTo(ExtensionPolicyType.CUSTOM);
            assertThat(history.getAction()).isEqualTo(ExtensionPolicyAction.ADD);
            assertThat(history.getExtension()).isIn("aaa", "bbb");
        });
    }

    @Test
    void 락_경합이_대기시간을_넘기면_50초가_아니라_명확한_409로_빠르게_실패한다() throws Exception {
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        try {
            // 다른 트랜잭션이 PolicyWriteLock 행을 붙잡은 채 놓아주지 않는 상황을 재현한다.
            Future<?> holder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                policyWriteLockRepository.findByNameForUpdate(PolicyWriteLock.CUSTOM_EXTENSION_LIMIT);
                lockHeld.countDown();
                try {
                    // 이 테스트가 확인하려는 3초 락 대기보다 충분히 길게 잡아,
                    // holder가 스스로 락을 놓아버려 검증이 무의미해지는 것을 방지한다.
                    releaseLock.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }));

            assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();

            long start = System.currentTimeMillis();
            mockMvc.perform(post("/api/policy/custom-extensions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"extension":"lockedout"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("다른 요청이 정책을 변경하는 중입니다. 잠시 후 다시 시도해주세요."));
            long elapsedMillis = System.currentTimeMillis() - start;

            // 락 대기시간을 3초로 짧게 잡아뒀으므로, MySQL 기본값(50초)까지 기다리지 않고 실패해야 한다.
            assertThat(elapsedMillis).isLessThan(10_000);

            releaseLock.countDown();
            holder.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(customExtensionRepository.count()).isZero();
    }

    private int requestStatus(CountDownLatch ready, CountDownLatch start, String extension) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(post("/api/policy/custom-extensions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"extension":"%s"}
                                """.formatted(extension)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private void assertErrorResponse(String extension, int status, String message) throws Exception {
        mockMvc.perform(post("/api/policy/custom-extensions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"extension":"%s"}
                                """.formatted(extension)))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}

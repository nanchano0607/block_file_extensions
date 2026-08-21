package com.chan.policy.repository;

import com.chan.policy.domain.PolicyWriteLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PolicyWriteLockRepository extends JpaRepository<PolicyWriteLock, String> {

    // MySQL의 innodb_lock_wait_timeout 기본값(50초)에 그대로 맡기면, 락을 오래 쥔 요청 뒤에
    // 대기하던 다른 요청이 50초 만에야 실패해서 알 수 없는 500으로 응답한다. 이 락은 저트래픽
    // 관리자 작업(200개 한도 체크)에만 쓰이므로, 짧게 대기하다 실패해 빠르고 명확한 409를
    // 돌려주는 쪽이 낫다고 판단했다.
    // MySQL은 (Oracle/PostgreSQL과 달리) `FOR UPDATE WAIT n` 구문이나 JPA의
    // jakarta.persistence.lock.timeout 힌트를 지원하지 않는다 — 세션 변수
    // innodb_lock_wait_timeout을 직접 낮추는 것이 유일한 방법이라, 락을 잡기 직전
    // 같은 트랜잭션(=같은 커넥션) 안에서 이 세션 변수를 먼저 설정한다.
    @Modifying
    @Query(value = "SET innodb_lock_wait_timeout = 3", nativeQuery = true)
    void setLockWaitTimeoutToThreeSeconds();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT lock FROM PolicyWriteLock lock WHERE lock.name = :name")
    Optional<PolicyWriteLock> findByNameForUpdate(@Param("name") String name);
}

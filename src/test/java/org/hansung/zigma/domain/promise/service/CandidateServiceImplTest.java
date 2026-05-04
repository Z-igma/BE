package org.hansung.zigma.domain.promise.service;

import org.hansung.zigma.domain.promise.entity.Candidate;
import org.hansung.zigma.domain.promise.entity.Promise;
import org.hansung.zigma.domain.promise.entity.PromiseMember;
import org.hansung.zigma.domain.promise.entity.PromiseStatus;
import org.hansung.zigma.domain.promise.entity.Role;
import org.hansung.zigma.domain.promise.exception.PromiseAlreadyConfirmedException;
import org.hansung.zigma.domain.promise.exception.PromiseMemberHostOnlyException;
import org.hansung.zigma.domain.promise.repository.CandidateRepository;
import org.hansung.zigma.domain.promise.repository.PromiseMemberRepository;
import org.hansung.zigma.domain.promise.web.dto.CandidateConfirmReq;
import org.hansung.zigma.domain.user.entity.User;
import org.hansung.zigma.domain.user.entity.UserProvider;
import org.hansung.zigma.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PromiseMemberRepository promiseMemberRepository;

    @Mock
    private CandidateRepository candidateRepository;

    @InjectMocks
    private CandidateServiceImpl candidateService;

    @Test
    @DisplayName("방장이 후보지를 확정하면 선택한 후보지만 확정되고 약속 상태가 CONFIRMED로 변경된다")
    void confirmCandidate_success() {
        // given: 방장, 약속, 후보지 2개를 준비하고 두 번째 후보를 확정 대상으로 선택
        Long userId = 1L;
        Long promiseId = 10L;
        Long confirmedCandidateId = 101L;

        User user = createUser(userId);
        Promise promise = createPromise(promiseId, PromiseStatus.PENDING);
        PromiseMember host = PromiseMember.createMember(user, promise, Role.HOST);

        Candidate firstCandidate = createCandidate(100L, promise, user, true);
        Candidate confirmedCandidate = createCandidate(confirmedCandidateId, promise, user, false);
        CandidateConfirmReq req = createConfirmReq(confirmedCandidateId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId)).thenReturn(Optional.of(host));
        when(candidateRepository.findByIdAndPromiseId(confirmedCandidateId, promiseId))
                .thenReturn(Optional.of(confirmedCandidate));
        when(candidateRepository.findAllByPromiseId(promiseId))
                .thenReturn(List.of(firstCandidate, confirmedCandidate));

        // when: 방장이 장소 확정 API 로직을 호출
        candidateService.confirmCandidate(userId, promiseId, req);

        // then: 모든 후보는 일단 미확정 처리되고, 선택된 후보만 다시 확정됨
        assertThat(firstCandidate.getIsConfirmed()).isFalse();
        assertThat(confirmedCandidate.getIsConfirmed()).isTrue();
        // 약속 전체 상태도 확정 완료로 변경되어야 함
        assertThat(promise.getStatus()).isEqualTo(PromiseStatus.CONFIRMED);
    }

    @Test
    @DisplayName("방장이 아닌 참여자가 장소 확정을 시도하면 예외가 발생한다")
    void confirmCandidate_failWhenMemberIsNotHost() {
        // given: 약속 참여자는 맞지만 역할이 MEMBER인 사용자
        Long userId = 1L;
        Long promiseId = 10L;
        Long candidateId = 101L;

        User user = createUser(userId);
        Promise promise = createPromise(promiseId, PromiseStatus.PENDING);
        PromiseMember member = PromiseMember.createMember(user, promise, Role.MEMBER);
        CandidateConfirmReq req = createConfirmReq(candidateId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId)).thenReturn(Optional.of(member));

        // when & then: 방장이 아니면 후보 조회 전에 바로 예외가 발생해야 함
        assertThatThrownBy(() -> candidateService.confirmCandidate(userId, promiseId, req))
                .isInstanceOf(PromiseMemberHostOnlyException.class);

        verify(candidateRepository, never()).findByIdAndPromiseId(candidateId, promiseId);
        verify(candidateRepository, never()).findAllByPromiseId(promiseId);
    }

    @Test
    @DisplayName("이미 확정된 약속은 다시 장소 확정할 수 없다")
    void confirmCandidate_failWhenPromiseAlreadyConfirmed() {
        // given: 약속 상태가 이미 CONFIRMED인 상태
        Long userId = 1L;
        Long promiseId = 10L;
        Long candidateId = 101L;

        User user = createUser(userId);
        Promise promise = createPromise(promiseId, PromiseStatus.CONFIRMED);
        PromiseMember host = PromiseMember.createMember(user, promise, Role.HOST);
        Candidate candidate = createCandidate(candidateId, promise, user, false);
        CandidateConfirmReq req = createConfirmReq(candidateId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId)).thenReturn(Optional.of(host));
        when(candidateRepository.findByIdAndPromiseId(candidateId, promiseId))
                .thenReturn(Optional.of(candidate));

        // when & then: 이미 확정된 약속이면 중복 확정을 막아야 함
        assertThatThrownBy(() -> candidateService.confirmCandidate(userId, promiseId, req))
                .isInstanceOf(PromiseAlreadyConfirmedException.class);

        verify(candidateRepository, never()).findAllByPromiseId(promiseId);
    }

    private CandidateConfirmReq createConfirmReq(Long candidateId) {
        // 테스트용 요청 DTO는 setter가 없어서 reflection으로 값만 주입
        CandidateConfirmReq req = new CandidateConfirmReq();
        ReflectionTestUtils.setField(req, "candidateId", candidateId);
        return req;
    }

    private User createUser(Long userId) {
        User user = User.create(
                UserProvider.LOCAL,
                "provider-id",
                "test@example.com",
                "tester",
                null
        );
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Promise createPromise(Long promiseId, PromiseStatus status) {
        Promise promise = Promise.builder()
                .title("test promise")
                .promisedAt(LocalDateTime.of(2026, 5, 10, 18, 0))
                .category(org.hansung.zigma.domain.promise.entity.Category.MEAL)
                .isMultipleVoting(true)
                .endAt(LocalDateTime.of(2026, 5, 10, 17, 0))
                .status(status)
                .build();
        ReflectionTestUtils.setField(promise, "id", promiseId);
        return promise;
    }

    private Candidate createCandidate(Long candidateId, Promise promise, User user, boolean isConfirmed) {
        Candidate candidate = Candidate.builder()
                .name("후보지")
                .address("서울시 어딘가")
                .latitude(37.0)
                .longitude(127.0)
                .category("식당")
                .isConfirmed(isConfirmed)
                .user(user)
                .promise(promise)
                .build();
        ReflectionTestUtils.setField(candidate, "id", candidateId);
        return candidate;
    }
}

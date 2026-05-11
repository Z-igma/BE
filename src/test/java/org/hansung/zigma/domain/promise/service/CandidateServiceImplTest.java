package org.hansung.zigma.domain.promise.service;

import org.hansung.zigma.domain.notification.event.PromiseConfirmedEvent;
import org.hansung.zigma.domain.promise.entity.Candidate;
import org.hansung.zigma.domain.promise.entity.CandidateVote;
import org.hansung.zigma.domain.promise.entity.Promise;
import org.hansung.zigma.domain.promise.entity.PromiseMember;
import org.hansung.zigma.domain.promise.entity.PromiseStatus;
import org.hansung.zigma.domain.promise.entity.Role;
import org.hansung.zigma.domain.promise.exception.CandidateInactiveException;
import org.hansung.zigma.domain.promise.exception.PromiseAlreadyConfirmedException;
import org.hansung.zigma.domain.promise.exception.PromiseMemberHostOnlyException;
import org.hansung.zigma.domain.promise.exception.PromiseRevoteNotAvailableException;
import org.hansung.zigma.domain.promise.repository.CandidateRepository;
import org.hansung.zigma.domain.promise.repository.CandidateVoteRepository;
import org.hansung.zigma.domain.promise.repository.PromiseMemberRepository;
import org.hansung.zigma.domain.promise.web.dto.CandidateConfirmReq;
import org.hansung.zigma.domain.user.entity.User;
import org.hansung.zigma.domain.user.entity.UserProvider;
import org.hansung.zigma.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
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

    @Mock
    private CandidateVoteRepository candidateVoteRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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

        ArgumentCaptor<PromiseConfirmedEvent> eventCaptor = ArgumentCaptor.forClass(PromiseConfirmedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().promiseId()).isEqualTo(promiseId);
        assertThat(eventCaptor.getValue().candidateId()).isEqualTo(confirmedCandidateId);
        assertThat(eventCaptor.getValue().candidateName()).isEqualTo(confirmedCandidate.getName());
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

    @Test
    @DisplayName("비활성 후보지는 장소 확정할 수 없다")
    void confirmCandidate_failWhenCandidateIsInactive() {
        // given: 방장이 맞지만 후보지가 현재 활성 후보가 아닌 상태
        Long userId = 1L;
        Long promiseId = 10L;
        Long candidateId = 101L;

        User user = createUser(userId);
        Promise promise = createPromise(promiseId, PromiseStatus.PENDING);
        PromiseMember host = PromiseMember.createMember(user, promise, Role.HOST);
        Candidate candidate = createCandidate(candidateId, promise, user, false, false);
        CandidateConfirmReq req = createConfirmReq(candidateId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId)).thenReturn(Optional.of(host));
        when(candidateRepository.findByIdAndPromiseId(candidateId, promiseId))
                .thenReturn(Optional.of(candidate));

        // when & then: 비활성 후보는 확정 대상이 될 수 없어야 함
        assertThatThrownBy(() -> candidateService.confirmCandidate(userId, promiseId, req))
                .isInstanceOf(CandidateInactiveException.class);

        verify(candidateRepository, never()).findAllByPromiseId(promiseId);
    }

    @Test
    @DisplayName("동점 후보가 2개 이상이면 해당 후보들만 남기고 재투표를 시작한다")
    void revoteCandidates_success() {
        // given: 방장이 재투표를 시작하고, 후보 2개가 같은 최다 득표수인 상황
        Long userId = 1L;
        Long promiseId = 10L;

        User user = createUser(userId);
        Promise promise = createPromise(promiseId, PromiseStatus.PENDING);
        PromiseMember host = PromiseMember.createMember(user, promise, Role.HOST);

        Candidate firstCandidate = createCandidate(100L, promise, user, false, true);
        Candidate secondCandidate = createCandidate(101L, promise, user, false, true);
        Candidate thirdCandidate = createCandidate(102L, promise, user, false, true);

        List<CandidateVote> votes = List.of(
                createVote(user, firstCandidate),
                createVote(createUser(2L), firstCandidate),
                createVote(createUser(3L), secondCandidate),
                createVote(createUser(4L), secondCandidate),
                createVote(createUser(5L), thirdCandidate)
        );

        LocalDateTime beforeRevote = LocalDateTime.now();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId)).thenReturn(Optional.of(host));
        when(candidateRepository.findAllByPromiseIdAndIsActiveTrue(promiseId))
                .thenReturn(List.of(firstCandidate, secondCandidate, thirdCandidate));
        when(candidateRepository.findAllByPromiseId(promiseId))
                .thenReturn(List.of(firstCandidate, secondCandidate, thirdCandidate));
        when(candidateVoteRepository.findAllByPromiseId(promiseId)).thenReturn(votes);

        // when: 재투표 시작
        candidateService.revoteCandidates(userId, promiseId);

        // then: 동점 후보 2개만 활성 상태로 남고, 나머지는 제외되어야 함
        assertThat(firstCandidate.getIsActive()).isTrue();
        assertThat(secondCandidate.getIsActive()).isTrue();
        assertThat(thirdCandidate.getIsActive()).isFalse();

        // 재투표는 단일 투표로 다시 열리고, 약속 상태는 진행 중으로 변경되어야 함
        assertThat(promise.getIsMultipleVoting()).isFalse();
        assertThat(promise.getStatus()).isEqualTo(PromiseStatus.PROCEEDING);
        assertThat(promise.getEndAt()).isAfter(beforeRevote.plusHours(11));

        // 기존 투표 기록은 전부 삭제되어야 함
        verify(candidateVoteRepository).deleteAllByPromiseId(promiseId);
    }

    @Test
    @DisplayName("재투표 대상 후보가 1개 이하이면 재투표를 시작할 수 없다")
    void revoteCandidates_failWhenRevoteCandidateCountIsLessThanTwo() {
        // given: 최다 득표 후보가 1개뿐이라 동점 재투표를 할 수 없는 상황
        Long userId = 1L;
        Long promiseId = 10L;

        User user = createUser(userId);
        Promise promise = createPromise(promiseId, PromiseStatus.PENDING);
        PromiseMember host = PromiseMember.createMember(user, promise, Role.HOST);

        Candidate firstCandidate = createCandidate(100L, promise, user, false, true);
        Candidate secondCandidate = createCandidate(101L, promise, user, false, true);

        List<CandidateVote> votes = List.of(
                createVote(user, firstCandidate),
                createVote(createUser(2L), firstCandidate),
                createVote(createUser(3L), secondCandidate)
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId)).thenReturn(Optional.of(host));
        when(candidateRepository.findAllByPromiseIdAndIsActiveTrue(promiseId))
                .thenReturn(List.of(firstCandidate, secondCandidate));
        when(candidateVoteRepository.findAllByPromiseId(promiseId)).thenReturn(votes);

        // when & then: 동점 후보가 2개 미만이면 예외가 발생해야 함
        assertThatThrownBy(() -> candidateService.revoteCandidates(userId, promiseId))
                .isInstanceOf(PromiseRevoteNotAvailableException.class);

        verify(candidateRepository, never()).findAllByPromiseId(promiseId);
        verify(candidateVoteRepository, never()).deleteAllByPromiseId(promiseId);
    }

    @Test
    @DisplayName("방장이 아닌 참여자가 재투표를 시작하면 예외가 발생한다")
    void revoteCandidates_failWhenMemberIsNotHost() {
        // given: 약속 참여자는 맞지만 역할이 MEMBER인 사용자
        Long userId = 1L;
        Long promiseId = 10L;

        User user = createUser(userId);
        Promise promise = createPromise(promiseId, PromiseStatus.PENDING);
        PromiseMember member = PromiseMember.createMember(user, promise, Role.MEMBER);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId)).thenReturn(Optional.of(member));

        // when & then: 방장이 아니면 재투표 후보 계산 전에 예외가 발생해야 함
        assertThatThrownBy(() -> candidateService.revoteCandidates(userId, promiseId))
                .isInstanceOf(PromiseMemberHostOnlyException.class);

        verify(candidateRepository, never()).findAllByPromiseIdAndIsActiveTrue(promiseId);
        verify(candidateVoteRepository, never()).findAllByPromiseId(promiseId);
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
        return createCandidate(candidateId, promise, user, isConfirmed, true);
    }

    private Candidate createCandidate(Long candidateId, Promise promise, User user, boolean isConfirmed, boolean isActive) {
        Candidate candidate = Candidate.builder()
                .name("후보지")
                .address("서울시 어딘가")
                .latitude(37.0)
                .longitude(127.0)
                .category("식당")
                .isConfirmed(isConfirmed)
                .isActive(isActive)
                .user(user)
                .promise(promise)
                .build();
        ReflectionTestUtils.setField(candidate, "id", candidateId);
        return candidate;
    }

    private CandidateVote createVote(User user, Candidate candidate) {
        return CandidateVote.createVote(user, candidate);
    }
}

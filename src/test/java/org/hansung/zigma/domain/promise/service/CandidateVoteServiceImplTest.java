package org.hansung.zigma.domain.promise.service;

import org.hansung.zigma.domain.promise.entity.Candidate;
import org.hansung.zigma.domain.promise.entity.CandidateVote;
import org.hansung.zigma.domain.promise.entity.Promise;
import org.hansung.zigma.domain.promise.entity.PromiseMember;
import org.hansung.zigma.domain.promise.entity.PromiseStatus;
import org.hansung.zigma.domain.promise.entity.Role;
import org.hansung.zigma.domain.promise.exception.CandidateInactiveException;
import org.hansung.zigma.domain.promise.exception.CandidateVoteConfirmationLockedException;
import org.hansung.zigma.domain.promise.exception.CandidateVoteDuplicatedException;
import org.hansung.zigma.domain.promise.exception.CandidateVoteMultipleNotAllowedException;
import org.hansung.zigma.domain.promise.exception.CandidateVoteNotFoundException;
import org.hansung.zigma.domain.promise.exception.PromiseVotingClosedException;
import org.hansung.zigma.domain.promise.repository.CandidateRepository;
import org.hansung.zigma.domain.promise.repository.CandidateVoteRepository;
import org.hansung.zigma.domain.promise.repository.PromiseMemberRepository;
import org.hansung.zigma.domain.promise.web.dto.CandidateVoteCreateReq;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateVoteServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PromiseMemberRepository promiseMemberRepository;

    @Mock
    private CandidateRepository candidateRepository;

    @Mock
    private CandidateVoteRepository candidateVoteRepository;

    @InjectMocks
    private CandidateVoteServiceImpl candidateVoteService;

    @Test
    @DisplayName("투표 성공: 단일 투표 정책이고 기존 투표가 없으면 저장된다")
    void createVote_success() {
        // given: 사용자, 약속, 후보지, 요청값을 준비
        Long userId = 1L;
        Long promiseId = 10L;
        Long candidateId = 100L;

        User user = createUser(userId);
        Promise promise = createPromise(promiseId, false);
        Candidate candidate = createCandidate(candidateId, promise, user);
        CandidateVoteCreateReq req = createVoteReq(candidateId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId))
                .thenReturn(Optional.of(PromiseMember.createMember(user, promise, Role.MEMBER)));
        when(candidateRepository.findByIdAndPromiseId(candidateId, promiseId)).thenReturn(Optional.of(candidate));
        when(candidateVoteRepository.findByUserIdAndCandidateId(userId, candidateId)).thenReturn(Optional.empty());
        when(candidateVoteRepository.existsByUserIdAndPromiseId(userId, promiseId)).thenReturn(false);

        // when: 실제 투표 생성 서비스 호출
        candidateVoteService.createVote(userId, promiseId, req);

        // then: 예외 없이 저장 로직이 한 번 호출되어야 함
        verify(candidateVoteRepository).save(any(CandidateVote.class));
    }

    @Test
    @DisplayName("같은 후보지 중복 투표면 예외가 발생한다")
    void createVote_failWhenDuplicatedCandidateVote() {
        // given: 이미 같은 후보지에 투표한 이력이 있다고 가정
        Long userId = 1L;
        Long promiseId = 10L;
        Long candidateId = 100L;

        User user = createUser(userId);
        Promise promise = createPromise(promiseId, true);
        Candidate candidate = createCandidate(candidateId, promise, user);
        CandidateVoteCreateReq req = createVoteReq(candidateId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId))
                .thenReturn(Optional.of(PromiseMember.createMember(user, promise, Role.MEMBER)));
        when(candidateRepository.findByIdAndPromiseId(candidateId, promiseId)).thenReturn(Optional.of(candidate));
        when(candidateVoteRepository.findByUserIdAndCandidateId(userId, candidateId))
                .thenReturn(Optional.of(CandidateVote.createVote(user, candidate)));

        // when & then: 서비스 호출 시 중복 투표 예외가 발생해야 함
        assertThatThrownBy(() -> candidateVoteService.createVote(userId, promiseId, req))
                .isInstanceOf(CandidateVoteDuplicatedException.class);

        // 예외가 났으므로 저장은 일어나면 안 됨
        verify(candidateVoteRepository, never()).save(any(CandidateVote.class));
    }

    @Test
    @DisplayName("단일 투표 정책에서 다른 후보에 이미 투표했으면 예외가 발생한다")
    void createVote_failWhenSingleVotingPolicyAndAlreadyVotedAnotherCandidate() {
        // given: 약속은 단일 투표 정책이고, 유저는 이미 이 약속에서 다른 후보에 투표한 상태
        Long userId = 1L;
        Long promiseId = 10L;
        Long candidateId = 100L;

        User user = createUser(userId);
        Promise promise = createPromise(promiseId, false);
        Candidate candidate = createCandidate(candidateId, promise, user);
        CandidateVoteCreateReq req = createVoteReq(candidateId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId))
                .thenReturn(Optional.of(PromiseMember.createMember(user, promise, Role.MEMBER)));
        when(candidateRepository.findByIdAndPromiseId(candidateId, promiseId)).thenReturn(Optional.of(candidate));
        when(candidateVoteRepository.findByUserIdAndCandidateId(userId, candidateId)).thenReturn(Optional.empty());
        when(candidateVoteRepository.existsByUserIdAndPromiseId(userId, promiseId)).thenReturn(true);

        // when & then: 다른 후보에 추가 투표하려 하면 정책 위반 예외가 발생해야 함
        assertThatThrownBy(() -> candidateVoteService.createVote(userId, promiseId, req))
                .isInstanceOf(CandidateVoteMultipleNotAllowedException.class);

        // 정책 검증에서 막혔으므로 저장은 일어나면 안 됨
        verify(candidateVoteRepository, never()).save(any(CandidateVote.class));
    }

    @Test
    @DisplayName("복수 투표 정책이면 다른 후보에 이미 투표했어도 추가 투표할 수 있다")
    void createVote_successWhenMultipleVotingPolicyAndAlreadyVotedAnotherCandidate() {
        // given: 약속은 복수 투표 허용 정책
        Long userId = 1L;
        Long promiseId = 10L;
        Long candidateId = 100L;

        User user = createUser(userId);
        Promise promise = createPromise(promiseId, true);
        Candidate candidate = createCandidate(candidateId, promise, user);
        CandidateVoteCreateReq req = createVoteReq(candidateId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId))
                .thenReturn(Optional.of(PromiseMember.createMember(user, promise, Role.MEMBER)));
        when(candidateRepository.findByIdAndPromiseId(candidateId, promiseId)).thenReturn(Optional.of(candidate));
        when(candidateVoteRepository.findByUserIdAndCandidateId(userId, candidateId)).thenReturn(Optional.empty());

        // when: 다른 후보에 추가 투표
        candidateVoteService.createVote(userId, promiseId, req);

        // then: 정상 저장되어야 함
        verify(candidateVoteRepository).save(any(CandidateVote.class));
        // 복수 투표 허용이면 "이미 약속에 투표했는지" 검사는 굳이 하지 않음
        verify(candidateVoteRepository, never()).existsByUserIdAndPromiseId(userId, promiseId);
    }

    @Test
    @DisplayName("투표 마감 시간이 지났으면 투표할 수 없다")
    void createVote_failWhenVotingClosed() {
        // given: 후보지는 존재하지만, 약속의 투표 종료 시간이 이미 지난 상태
        Long userId = 1L;
        Long promiseId = 10L;
        Long candidateId = 100L;

        User user = createUser(userId);
        Promise promise = createClosedPromise(promiseId, true);
        Candidate candidate = createCandidate(candidateId, promise, user);
        CandidateVoteCreateReq req = createVoteReq(candidateId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId))
                .thenReturn(Optional.of(PromiseMember.createMember(user, promise, Role.MEMBER)));
        when(candidateRepository.findByIdAndPromiseId(candidateId, promiseId)).thenReturn(Optional.of(candidate));

        // when & then: 마감 후 요청이면 즉시 예외가 발생해야 함
        assertThatThrownBy(() -> candidateVoteService.createVote(userId, promiseId, req))
                .isInstanceOf(PromiseVotingClosedException.class);

        // 마감 검증에서 막혔으므로 이후 저장/중복 검사는 진행되지 않음
        verify(candidateVoteRepository, never()).findByUserIdAndCandidateId(any(), any());
        verify(candidateVoteRepository, never()).existsByUserIdAndPromiseId(any(), any());
        verify(candidateVoteRepository, never()).save(any(CandidateVote.class));
    }

    @Test
    @DisplayName("약속이 이미 확정 상태면 투표할 수 없다")
    void createVote_failWhenPromiseAlreadyConfirmed() {
        // given: 약속 전체 상태가 이미 확정 완료인 상태
        Long userId = 1L;
        Long promiseId = 10L;
        Long candidateId = 100L;

        User user = createUser(userId);
        Promise promise = createPromise(promiseId, true, PromiseStatus.CONFIRMED);
        Candidate candidate = createCandidate(candidateId, promise, user);
        CandidateVoteCreateReq req = createVoteReq(candidateId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId))
                .thenReturn(Optional.of(PromiseMember.createMember(user, promise, Role.MEMBER)));
        when(candidateRepository.findByIdAndPromiseId(candidateId, promiseId)).thenReturn(Optional.of(candidate));

        // when & then: 확정된 약속에는 더 이상 투표할 수 없어야 함
        assertThatThrownBy(() -> candidateVoteService.createVote(userId, promiseId, req))
                .isInstanceOf(CandidateVoteConfirmationLockedException.class);

        verify(candidateVoteRepository, never()).findByUserIdAndCandidateId(any(), any());
        verify(candidateVoteRepository, never()).existsByUserIdAndPromiseId(any(), any());
        verify(candidateVoteRepository, never()).save(any(CandidateVote.class));
    }

    @Test
    @DisplayName("후보지가 이미 확정 상태면 투표할 수 없다")
    void createVote_failWhenCandidateAlreadyConfirmed() {
        // given: 약속은 진행 중이지만 특정 후보지가 이미 확정된 상태
        Long userId = 1L;
        Long promiseId = 10L;
        Long candidateId = 100L;

        User user = createUser(userId);
        Promise promise = createPromise(promiseId, true);
        Candidate candidate = createCandidate(candidateId, promise, user, true);
        CandidateVoteCreateReq req = createVoteReq(candidateId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId))
                .thenReturn(Optional.of(PromiseMember.createMember(user, promise, Role.MEMBER)));
        when(candidateRepository.findByIdAndPromiseId(candidateId, promiseId)).thenReturn(Optional.of(candidate));

        // when & then: 확정 후보에는 더 이상 투표할 수 없어야 함
        assertThatThrownBy(() -> candidateVoteService.createVote(userId, promiseId, req))
                .isInstanceOf(CandidateVoteConfirmationLockedException.class);

        verify(candidateVoteRepository, never()).findByUserIdAndCandidateId(any(), any());
        verify(candidateVoteRepository, never()).existsByUserIdAndPromiseId(any(), any());
        verify(candidateVoteRepository, never()).save(any(CandidateVote.class));
    }

    @Test
    @DisplayName("비활성 후보지에는 투표할 수 없다")
    void createVote_failWhenCandidateIsInactive() {
        // given: 약속은 진행 중이지만 후보지가 현재 투표 대상에서 제외된 상태
        Long userId = 1L;
        Long promiseId = 10L;
        Long candidateId = 100L;

        User user = createUser(userId);
        Promise promise = createPromise(promiseId, true);
        Candidate candidate = createCandidate(candidateId, promise, user, false, false);
        CandidateVoteCreateReq req = createVoteReq(candidateId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId))
                .thenReturn(Optional.of(PromiseMember.createMember(user, promise, Role.MEMBER)));
        when(candidateRepository.findByIdAndPromiseId(candidateId, promiseId)).thenReturn(Optional.of(candidate));

        // when & then: 비활성 후보에는 투표할 수 없어야 함
        assertThatThrownBy(() -> candidateVoteService.createVote(userId, promiseId, req))
                .isInstanceOf(CandidateInactiveException.class);

        verify(candidateVoteRepository, never()).findByUserIdAndCandidateId(any(), any());
        verify(candidateVoteRepository, never()).existsByUserIdAndPromiseId(any(), any());
        verify(candidateVoteRepository, never()).save(any(CandidateVote.class));
    }

    @Test
    @DisplayName("내가 한 투표는 정상적으로 취소할 수 있다")
    void cancelVote_success() {
        // given: 사용자가 해당 후보에 직접 투표한 기록이 있는 상태
        Long userId = 1L;
        Long promiseId = 10L;
        Long candidateId = 100L;

        User user = createUser(userId);
        Promise promise = createPromise(promiseId, true);
        Candidate candidate = createCandidate(candidateId, promise, user);
        CandidateVote candidateVote = CandidateVote.createVote(user, candidate);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId))
                .thenReturn(Optional.of(PromiseMember.createMember(user, promise, Role.MEMBER)));
        when(candidateRepository.findByIdAndPromiseId(candidateId, promiseId)).thenReturn(Optional.of(candidate));
        when(candidateVoteRepository.findByUserIdAndCandidateId(userId, candidateId))
                .thenReturn(Optional.of(candidateVote));

        // when: 내 투표 취소 요청
        candidateVoteService.cancelVote(userId, promiseId, candidateId);

        // then: 내 투표 기록이 삭제되어야 함
        verify(candidateVoteRepository).delete(candidateVote);
    }

    @Test
    @DisplayName("내가 투표하지 않은 후보를 취소하려 하면 예외가 발생한다")
    void cancelVote_failWhenUserDidNotVote() {
        // given: 후보지는 존재하지만 현재 사용자가 한 투표 기록은 없는 상태
        Long userId = 1L;
        Long promiseId = 10L;
        Long candidateId = 100L;

        User user = createUser(userId);
        Promise promise = createPromise(promiseId, true);
        Candidate candidate = createCandidate(candidateId, promise, user);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId))
                .thenReturn(Optional.of(PromiseMember.createMember(user, promise, Role.MEMBER)));
        when(candidateRepository.findByIdAndPromiseId(candidateId, promiseId)).thenReturn(Optional.of(candidate));
        when(candidateVoteRepository.findByUserIdAndCandidateId(userId, candidateId))
                .thenReturn(Optional.empty());

        // when & then: 내가 넣지 않은 표는 취소할 수 없어야 함
        assertThatThrownBy(() -> candidateVoteService.cancelVote(userId, promiseId, candidateId))
                .isInstanceOf(CandidateVoteNotFoundException.class);

        verify(candidateVoteRepository, never()).delete(any(CandidateVote.class));
    }

    private CandidateVoteCreateReq createVoteReq(Long candidateId) {
        // 테스트용 요청 DTO는 setter가 없어서 reflection으로 값만 주입
        CandidateVoteCreateReq req = new CandidateVoteCreateReq();
        ReflectionTestUtils.setField(req, "candidateId", candidateId);
        return req;
    }

    private User createUser(Long userId) {
        // 실제 엔티티 생성 메서드를 쓰고, 테스트에 필요한 id만 따로 주입
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

    private Promise createPromise(Long promiseId, boolean isMultipleVoting) {
        return createPromise(promiseId, isMultipleVoting, PromiseStatus.PENDING);
    }

    private Promise createPromise(Long promiseId, boolean isMultipleVoting, PromiseStatus status) {
        // 투표 정책(true/false)만 바꿔가며 재사용할 수 있게 약속 fixture 생성
        Promise promise = Promise.builder()
                .title("test promise")
                .promisedAt(LocalDateTime.of(2026, 5, 10, 18, 0))
                .category(org.hansung.zigma.domain.promise.entity.Category.MEAL)
                .isMultipleVoting(isMultipleVoting)
                .endAt(LocalDateTime.of(2026, 5, 10, 17, 0))
                .status(status)
                .build();
        ReflectionTestUtils.setField(promise, "id", promiseId);
        return promise;
    }

    private Promise createClosedPromise(Long promiseId, boolean isMultipleVoting) {
        // 이미 투표 종료 시간이 지난 약속 fixture
        Promise promise = Promise.builder()
                .title("closed promise")
                .promisedAt(LocalDateTime.now().plusHours(1))
                .category(org.hansung.zigma.domain.promise.entity.Category.MEAL)
                .isMultipleVoting(isMultipleVoting)
                .endAt(LocalDateTime.now().minusMinutes(1))
                .status(org.hansung.zigma.domain.promise.entity.PromiseStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(promise, "id", promiseId);
        return promise;
    }

    private Candidate createCandidate(Long candidateId, Promise promise, User user) {
        return createCandidate(candidateId, promise, user, false);
    }

    private Candidate createCandidate(Long candidateId, Promise promise, User user, boolean isConfirmed) {
        return createCandidate(candidateId, promise, user, isConfirmed, true);
    }

    private Candidate createCandidate(Long candidateId, Promise promise, User user, boolean isConfirmed, boolean isActive) {
        // 후보지는 반드시 특정 약속과 작성자 유저에 연결되어 있어야 함
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
}

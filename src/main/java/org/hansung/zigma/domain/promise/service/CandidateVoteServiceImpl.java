package org.hansung.zigma.domain.promise.service;

import lombok.RequiredArgsConstructor;
import org.hansung.zigma.domain.promise.entity.Candidate;
import org.hansung.zigma.domain.promise.entity.CandidateVote;
import org.hansung.zigma.domain.promise.exception.CandidateNotFoundException;
import org.hansung.zigma.domain.promise.exception.CandidateVoteConfirmationLockedException;
import org.hansung.zigma.domain.promise.exception.CandidateVoteDuplicatedException;
import org.hansung.zigma.domain.promise.exception.CandidateVoteMultipleNotAllowedException;
import org.hansung.zigma.domain.promise.entity.PromiseStatus;
import org.hansung.zigma.domain.promise.exception.PromiseMemberAccessDeniedException;
import org.hansung.zigma.domain.promise.exception.PromiseVotingClosedException;
import org.hansung.zigma.domain.promise.repository.CandidateRepository;
import org.hansung.zigma.domain.promise.repository.CandidateVoteRepository;
import org.hansung.zigma.domain.promise.repository.PromiseMemberRepository;
import org.hansung.zigma.domain.promise.web.dto.CandidateVoteCreateReq;
import org.hansung.zigma.domain.user.entity.User;
import org.hansung.zigma.domain.user.exception.UserNotFoundException;
import org.hansung.zigma.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CandidateVoteServiceImpl implements CandidateVoteService {

    private final UserRepository userRepository;
    private final PromiseMemberRepository promiseMemberRepository;
    private final CandidateRepository candidateRepository;
    private final CandidateVoteRepository candidateVoteRepository;

    @Override
    @Transactional
    public void createVote(Long userId, Long promiseId, CandidateVoteCreateReq req) {
        // 1. 인증된 사용자 자체가 유효한지 확인
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        // 2. 해당 사용자가 이 약속의 참여자인지 확인
        promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId)
                .orElseThrow(PromiseMemberAccessDeniedException::new);

        // 3. 요청한 후보지가 실제로 이 약속에 속한 후보지인지 확인
        Candidate candidate = candidateRepository.findByIdAndPromiseId(req.getCandidateId(), promiseId)
                .orElseThrow(CandidateNotFoundException::new);

        // 4. 투표 종료 시간이 현재보다 이전이거나 같으면 더 이상 투표할 수 없음
        if (!candidate.getPromise().getEndAt().isAfter(LocalDateTime.now())) {
            throw new PromiseVotingClosedException();
        }

        // 5. 약속 전체가 확정되었거나 해당 후보지가 확정되었으면 더 이상 투표할 수 없음
        if (candidate.getPromise().getStatus() == PromiseStatus.CONFIRMED || candidate.getIsConfirmed()) {
            throw new CandidateVoteConfirmationLockedException();
        }

        // 6. 같은 후보지에 대한 중복 투표는 항상 금지
        if (candidateVoteRepository.findByUserIdAndCandidateId(userId, candidate.getId()).isPresent()) {
            throw new CandidateVoteDuplicatedException();
        }

        // 7. 약속이 단일 투표 정책이면, 같은 약속 내 다른 후보지 추가 투표도 금지
        if (!candidate.getPromise().getIsMultipleVoting()
                && candidateVoteRepository.existsByUserIdAndPromiseId(userId, promiseId)) {
            throw new CandidateVoteMultipleNotAllowedException();
        }

        // 8. 모든 검증을 통과하면 투표 엔티티를 생성하고 저장
        CandidateVote candidateVote = CandidateVote.createVote(user, candidate);
        candidateVoteRepository.save(candidateVote);
    }
}

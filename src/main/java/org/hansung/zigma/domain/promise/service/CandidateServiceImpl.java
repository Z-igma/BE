package org.hansung.zigma.domain.promise.service;

import lombok.RequiredArgsConstructor;
import org.hansung.zigma.domain.promise.entity.Candidate;
import org.hansung.zigma.domain.promise.entity.PromiseStatus;
import org.hansung.zigma.domain.promise.entity.PromiseMember;
import org.hansung.zigma.domain.promise.entity.Role;
import org.hansung.zigma.domain.promise.exception.CandidateNotFoundException;
import org.hansung.zigma.domain.promise.exception.PromiseAlreadyConfirmedException;
import org.hansung.zigma.domain.promise.exception.PromiseMemberAccessDeniedException;
import org.hansung.zigma.domain.promise.exception.PromiseMemberHostOnlyException;
import org.hansung.zigma.domain.promise.repository.CandidateRepository;
import org.hansung.zigma.domain.promise.repository.PromiseMemberRepository;
import org.hansung.zigma.domain.promise.web.dto.CandidateConfirmReq;
import org.hansung.zigma.domain.promise.web.dto.CandidateCreateReq;
import org.hansung.zigma.domain.promise.web.dto.CandidateListRes;
import org.hansung.zigma.domain.promise.web.dto.CandidateRes;
import org.hansung.zigma.domain.user.entity.User;
import org.hansung.zigma.domain.user.exception.UserNotFoundException;
import org.hansung.zigma.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CandidateServiceImpl implements CandidateService {

    private final UserRepository userRepository;
    private final PromiseMemberRepository promiseMemberRepository;
    private final CandidateRepository candidateRepository;

    @Override
    @Transactional
    public CandidateRes createCandidate(Long userId, Long promiseId, CandidateCreateReq req) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        PromiseMember pm = promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId)
                .orElseThrow(PromiseMemberAccessDeniedException::new);

        Candidate candidate = Candidate.createCandidate(
                req, user, pm.getPromise()
        );
        Candidate savedCandidate = candidateRepository.save(candidate);

        return CandidateRes.from(savedCandidate);
    }

    @Override
    public CandidateListRes getCandidates(Long userId, Long promiseId) {
        userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId)
                .orElseThrow(PromiseMemberAccessDeniedException::new);

        // 현재 투표 대상으로 살아있는 후보지만 반환
        List<Candidate> candidates = candidateRepository.findAllByPromiseIdAndIsActiveTrue(promiseId);
        List<CandidateRes> res = candidates.stream()
                .map(CandidateRes::from)
                .toList();

        return CandidateListRes.from(res);
    }

    @Override
    @Transactional
    public void confirmCandidate(Long userId, Long promiseId, CandidateConfirmReq req) {
        // 1. 인증된 사용자 자체가 유효한지 확인
        userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        // 2. 해당 사용자가 이 약속의 참여자인지 확인
        PromiseMember promiseMember = promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId)
                .orElseThrow(PromiseMemberAccessDeniedException::new);

        // 3. 장소 확정은 방장만 가능
        if (promiseMember.getRole() != Role.HOST) {
            throw new PromiseMemberHostOnlyException();
        }

        // 4. 요청한 후보지가 실제로 이 약속에 속한 후보지인지 확인
        Candidate confirmedCandidate = candidateRepository.findByIdAndPromiseId(req.getCandidateId(), promiseId)
                .orElseThrow(CandidateNotFoundException::new);

        // 5. 이미 확정된 약속이면 중복 확정을 막음
        if (confirmedCandidate.getPromise().getStatus() == PromiseStatus.CONFIRMED) {
            throw new PromiseAlreadyConfirmedException();
        }

        // 6. 같은 약속의 후보지들을 모두 미확정 처리한 뒤
        //    선택한 후보지만 확정 상태로 변경
        List<Candidate> candidates = candidateRepository.findAllByPromiseId(promiseId);
        candidates.forEach(Candidate::unconfirm);
        confirmedCandidate.confirm();

        // 7. 약속 전체 상태도 확정 완료로 변경
        confirmedCandidate.getPromise().confirm();
    }
}

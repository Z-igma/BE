package org.hansung.zigma.domain.promise.service;

import lombok.RequiredArgsConstructor;
import org.hansung.zigma.domain.promise.entity.Candidate;
import org.hansung.zigma.domain.promise.entity.PromiseMember;
import org.hansung.zigma.domain.promise.exception.PromiseMemberAccessDeniedException;
import org.hansung.zigma.domain.promise.exception.PromiseNotFoundException;
import org.hansung.zigma.domain.promise.repository.CandidateRepository;
import org.hansung.zigma.domain.promise.repository.PromiseMemberRepository;
import org.hansung.zigma.domain.promise.web.dto.CandidateCreateReq;
import org.hansung.zigma.domain.promise.web.dto.CandidateListRes;
import org.hansung.zigma.domain.promise.web.dto.CandidateRes;
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
        userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        promiseMemberRepository.findById(promiseId)
                .orElseThrow(PromiseNotFoundException::new);

        PromiseMember pm = promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId)
                .orElseThrow(PromiseMemberAccessDeniedException::new);

        Candidate candidate = Candidate.createCandidate(
                req, pm.getUser(), pm.getPromise()
        );
        Candidate savedCandidate = candidateRepository.save(candidate);

        return CandidateRes.from(savedCandidate);
    }

    @Override
    public CandidateListRes getCandidates(Long userId, Long promiseId) {
        userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        promiseMemberRepository.findById(promiseId)
                .orElseThrow(PromiseNotFoundException::new);

        promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId)
                .orElseThrow(PromiseMemberAccessDeniedException::new);

        List<Candidate> candidates = candidateRepository.findAllByPromiseId(promiseId);
        List<CandidateRes> res = candidates.stream()
                .map(CandidateRes::from)
                .toList();

        return CandidateListRes.from(res);
    }
}

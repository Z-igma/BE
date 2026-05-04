package org.hansung.zigma.domain.promise.web.dto;

import org.hansung.zigma.domain.promise.entity.CandidateVote;

public record CandidateVoteRes(
        Long id,
        Long promiseId,
        Long candidateId,
        Long userId
) {
    public static CandidateVoteRes from(CandidateVote candidateVote) {
        return new CandidateVoteRes(
                candidateVote.getId(),
                candidateVote.getPromise().getId(),
                candidateVote.getCandidate().getId(),
                candidateVote.getUser().getId()
        );
    }
}

package org.hansung.zigma.domain.promise.service;

import org.hansung.zigma.domain.promise.web.dto.CandidateVoteCreateReq;
import org.hansung.zigma.domain.promise.web.dto.CandidateVoteRes;

public interface CandidateVoteService {

    CandidateVoteRes createVote(Long userId, Long promiseId, CandidateVoteCreateReq req);
}

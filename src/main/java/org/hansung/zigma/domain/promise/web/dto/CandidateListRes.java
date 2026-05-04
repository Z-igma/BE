package org.hansung.zigma.domain.promise.web.dto;

import java.util.List;

public record CandidateListRes(
        List<CandidateRes> candidates,
        Integer count
) {
    public static CandidateListRes from(List<CandidateRes> candidates) {
        return new CandidateListRes(
                candidates,
                candidates.size()
        );
    }
}
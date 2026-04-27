package org.hansung.zigma.domain.promise.web.dto;

import org.hansung.zigma.domain.promise.entity.Candidate;

public record CandidateRes(
        Long id,
        String name,
        String category,
        String address,
        Double distance,
        Boolean isConfirmed

        /* 투표 관련 데이터
        Integer voteCount,
        Integer totalMemberCount,
        List<String> voterNames
        */
) {
    public static CandidateRes from(Candidate candidate) {
        return new CandidateRes(
                candidate.getId(),
                candidate.getName(),
                candidate.getCategory(),
                candidate.getAddress(),
                120.0,
                candidate.getIsConfirmed()
        );
    }
}

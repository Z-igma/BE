package org.hansung.zigma.domain.promise.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hansung.zigma.global.response.code.BaseResponseCode;

@Getter
@AllArgsConstructor
public enum CandidateErrorCode implements BaseResponseCode {

    CANDIDATE_NOT_FOUND("CANDIDATE_404_1", 404, "해당 후보지를 찾을 수 없습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}

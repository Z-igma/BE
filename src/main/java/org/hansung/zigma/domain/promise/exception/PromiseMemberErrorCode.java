package org.hansung.zigma.domain.promise.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hansung.zigma.global.response.code.BaseResponseCode;

@Getter
@AllArgsConstructor
public enum PromiseMemberErrorCode implements BaseResponseCode {

    PROMISE_MEMBER_ACCESS_DENIED("PROMISE_MEMBER_403_1", 403, "해당 약속의 참여자가 아닙니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}

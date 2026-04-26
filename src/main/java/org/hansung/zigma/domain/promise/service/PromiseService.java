package org.hansung.zigma.domain.promise.service;

import org.hansung.zigma.domain.promise.web.dto.PromiseCreateReq;
import org.hansung.zigma.domain.promise.web.dto.PromiseListRes;
import org.hansung.zigma.domain.promise.web.dto.PromiseRes;

public interface PromiseService {
    // 약속 생성
    PromiseRes createPromise(Long userId, PromiseCreateReq planCreateReq);
    // 약속 전체 조회
    PromiseListRes getPromises(Long userId, String encodedCursor, int size);
}

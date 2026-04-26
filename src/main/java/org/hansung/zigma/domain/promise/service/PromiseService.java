package org.hansung.zigma.domain.promise.service;

import org.hansung.zigma.domain.promise.web.dto.PromiseCreateReq;
import org.hansung.zigma.domain.promise.web.dto.PromiseRes;

public interface PromiseService {
    // 약속 생성
    PromiseRes createPromise(Long userId, PromiseCreateReq planCreateReq);
}

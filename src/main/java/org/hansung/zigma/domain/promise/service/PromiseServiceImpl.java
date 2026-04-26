package org.hansung.zigma.domain.promise.service;

import lombok.RequiredArgsConstructor;
import org.hansung.zigma.domain.promise.entity.Promise;
import org.hansung.zigma.domain.promise.repository.PromiseRepository;
import org.hansung.zigma.domain.promise.util.Validator;
import org.hansung.zigma.domain.promise.web.dto.PromiseCreateReq;
import org.hansung.zigma.domain.promise.web.dto.PromiseRes;
import org.hansung.zigma.domain.user.entity.User;
import org.hansung.zigma.domain.user.exception.UserNotFoundException;
import org.hansung.zigma.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromiseServiceImpl implements PromiseService {

    private final UserRepository userRepository;
    private final PromiseRepository promiseRepository;

    @Override
    @Transactional
    public PromiseRes createPromise(Long userId, PromiseCreateReq req) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        Promise promise = Promise.toEntity(req);

        Validator.validatePlanDates(promise.getPromisedAt(), promise.getEndAt());

        Promise savedPlan = promiseRepository.save(promise);

        return PromiseRes.from(savedPlan);
    }
}

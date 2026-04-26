package org.hansung.zigma.domain.promise.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hansung.zigma.domain.promise.service.PromiseService;
import org.hansung.zigma.domain.promise.web.dto.PromiseCreateReq;
import org.hansung.zigma.domain.promise.web.dto.PromiseRes;
import org.hansung.zigma.global.jwt.CustomUserDetails;
import org.hansung.zigma.global.response.SuccessResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/promises")
public class PromiseController {

    private final PromiseService promiseService;

    @PostMapping
    public ResponseEntity<SuccessResponse<PromiseRes>> createProgram(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @RequestBody @Valid PromiseCreateReq req
    ) {
        Long userId = Long.parseLong(customUserDetails.getUsername());

        PromiseRes res = promiseService.createPromise(userId, req);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.created(res));
    }
}

package org.hansung.zigma.domain.comment.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hansung.zigma.domain.comment.repository.CommentRepository;
import org.hansung.zigma.domain.comment.service.CommentService;
import org.hansung.zigma.domain.comment.web.dto.CommentCreateReq;
import org.hansung.zigma.domain.comment.web.dto.CommentRes;
import org.hansung.zigma.global.jwt.CustomUserDetails;
import org.hansung.zigma.global.response.SuccessResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/promises")
public class CommentController {
    private final CommentService commentService;

    @PostMapping("/{promiseId}/comments")
    public ResponseEntity<SuccessResponse<CommentRes>> createComment(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long promiseId,
            @RequestBody @Valid CommentCreateReq req
    ) {
        Long userId = Long.parseLong(customUserDetails.getUsername());

        CommentRes res = commentService.createComment(userId, promiseId, req);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.created(res));
    }

}

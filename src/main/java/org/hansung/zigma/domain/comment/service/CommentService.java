package org.hansung.zigma.domain.comment.service;

import org.hansung.zigma.domain.comment.web.dto.CommentCreateReq;
import org.hansung.zigma.domain.comment.web.dto.CommentRes;

public interface CommentService {
    // 코멘트 생성
    CommentRes createComment(Long userId, Long promiseId, CommentCreateReq req);
}

package org.hansung.zigma.domain.user.web.dto;

import org.hansung.zigma.domain.user.entity.User;

public record UserRes(
        Long userId,
        String email,
        String nickname,
        String profileImageUrl,
        String bio,
        int joinedPromiseCount,  // 참여중인 약속 수
        int hostedPromiseCount    // 내가 만든 약속 수
) {
    public static UserRes of(
            User user,
            int joinedPromiseCount,
            int hostedPromiseCount
    ) {
        return new UserRes(
                user.getId(),
                user.getEmail(),
                user.getNickName(),
                user.getProfileImageUrl(),
                user.getBio(),
                joinedPromiseCount,
                hostedPromiseCount
        );
    }
}

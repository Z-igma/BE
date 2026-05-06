package org.hansung.zigma.domain.promise.web.dto;

import org.hansung.zigma.domain.promise.entity.Promise;

import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

public record PromiseDetailRes(
        Long id,
        String title,
        LocalDateTime promisedAt,
        String dayOfWeek,
        Boolean isMultipleVoting,
        Integer memberCount,
        List<PromiseMemberRes> members
) {
    public static PromiseDetailRes from(Promise promise) {
        String dayOfWeek = promise.getPromisedAt()
                .getDayOfWeek()
                .getDisplayName(TextStyle.SHORT, Locale.KOREAN);

        List<PromiseMemberRes> members = promise.getPromiseMembers().stream()
                .map(PromiseMemberRes::from)
                .toList();

        return new PromiseDetailRes(
                promise.getId(),
                promise.getTitle(),
                promise.getPromisedAt(),
                dayOfWeek,
                promise.getIsMultipleVoting(),
                promise.getPromiseMembers().size(),
                members
        );
    }
}
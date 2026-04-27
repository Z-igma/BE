package org.hansung.zigma.domain.promise.web.dto;

import org.hansung.zigma.domain.promise.entity.Promise;
import org.hansung.zigma.domain.promise.entity.PromiseStatus;

import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

public record PromiseRes(
        Long id,
        String title,
        PromiseStatus promiseStatus,
        LocalDateTime promisedAt,
        String dayOfWeek,
        Integer memberCount
) {
    public static PromiseRes from(Promise promise) {
        String dayOfWeek = promise.getPromisedAt()
                .getDayOfWeek()
                .getDisplayName(TextStyle.SHORT, Locale.KOREAN);

        return new PromiseRes(
                promise.getId(),
                promise.getTitle(),
                promise.getStatus(),
                promise.getPromisedAt(),
                dayOfWeek,
                promise.getPromiseMembers().size()
        );
    }
}

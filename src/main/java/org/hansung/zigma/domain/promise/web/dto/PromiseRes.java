package org.hansung.zigma.domain.promise.web.dto;

import org.hansung.zigma.domain.promise.entity.Promise;
import org.hansung.zigma.domain.promise.entity.PlanStatus;

import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

public record PromiseRes(
        Long id,
        String title,
        PlanStatus planStatus,
        LocalDateTime promisedAt,
        String dayOfWeek,
        Integer memberCount
) {
    public static PromiseRes from(Promise plan) {
        String dayOfWeek = plan.getPromisedAt()
                .getDayOfWeek()
                .getDisplayName(TextStyle.SHORT, Locale.KOREAN);

        return new PromiseRes(
                plan.getId(),
                plan.getTitle(),
                plan.getStatus(),
                plan.getPromisedAt(),
                dayOfWeek,
                plan.getPromiseMembers().size()
        );
    }
}

package org.hansung.zigma.domain.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hansung.zigma.domain.notification.entity.Notification;
import org.hansung.zigma.domain.notification.entity.NotificationType;
import org.hansung.zigma.domain.notification.entity.PushSubscription;
import org.hansung.zigma.domain.notification.event.PromiseConfirmedEvent;
import org.hansung.zigma.domain.notification.repository.NotificationRepository;
import org.hansung.zigma.domain.notification.repository.PushSubscriptionRepository;
import org.hansung.zigma.domain.promise.entity.Promise;
import org.hansung.zigma.domain.promise.entity.PromiseMember;
import org.hansung.zigma.domain.promise.entity.PromiseStatus;
import org.hansung.zigma.domain.promise.entity.Role;
import org.hansung.zigma.domain.promise.repository.PromiseMemberRepository;
import org.hansung.zigma.domain.user.entity.User;
import org.hansung.zigma.domain.user.entity.UserProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromiseNotificationServiceTest {

    @Mock
    private PromiseMemberRepository promiseMemberRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;

    @Mock
    private WebPushSender webPushSender;

    private PromiseNotificationService promiseNotificationService;

    @BeforeEach
    void setUp() {
        promiseNotificationService = new PromiseNotificationService(
                promiseMemberRepository,
                notificationRepository,
                pushSubscriptionRepository,
                webPushSender,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("약속 장소 확정 이벤트가 오면 멤버들의 활성 구독으로 웹 푸시를 발송하고 알림 이력을 저장한다")
    void sendPromiseConfirmedNotification_success() {
        // given
        Long promiseId = 10L;
        User host = createUser(1L);
        User member = createUser(2L);
        Promise promise = createPromise(promiseId);
        PromiseMember hostMember = PromiseMember.createMember(host, promise, Role.HOST);
        PromiseMember promiseMember = PromiseMember.createMember(member, promise, Role.MEMBER);
        PushSubscription hostSubscription = createSubscription(100L, host, "https://push.example.com/host");
        PushSubscription memberSubscription = createSubscription(101L, member, "https://push.example.com/member");
        PromiseConfirmedEvent event = createEvent(promiseId);

        when(promiseMemberRepository.findAllByPromiseId(promiseId))
                .thenReturn(List.of(hostMember, promiseMember));
        when(pushSubscriptionRepository.findAllByUserIdInAndIsActiveTrue(List.of(1L, 2L)))
                .thenReturn(List.of(hostSubscription, memberSubscription));
        when(webPushSender.send(eq(hostSubscription), any(String.class)))
                .thenReturn(new WebPushSendResult(true, 201));
        when(webPushSender.send(eq(memberSubscription), any(String.class)))
                .thenReturn(new WebPushSendResult(true, 201));

        // when
        promiseNotificationService.sendPromiseConfirmedNotification(event);

        // then
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(webPushSender).send(eq(hostSubscription), payloadCaptor.capture());
        verify(webPushSender).send(eq(memberSubscription), any(String.class));

        assertThat(payloadCaptor.getValue())
                .contains("\"type\":\"PROMISE_CONFIRMED\"")
                .contains("\"title\":\"약속 장소가 확정됐어요\"")
                .contains("\"url\":\"/promises/10\"");
        assertThat(hostSubscription.getLastUsedAt()).isNotNull();
        assertThat(memberSubscription.getLastUsedAt()).isNotNull();

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(notificationCaptor.capture());

        List<Notification> notifications = notificationCaptor.getAllValues();
        assertThat(notifications).hasSize(2);
        assertThat(notifications)
                .allSatisfy(notification -> {
                    assertThat(notification.getType()).isEqualTo(NotificationType.PROMISE_CONFIRMED);
                    assertThat(notification.getTargetId()).isEqualTo(promiseId);
                    assertThat(notification.getLinkUrl()).isEqualTo("/promises/10");
                    assertThat(notification.getSentAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("웹 푸시 응답이 410이면 구독을 만료 처리하고 알림 이력은 미발송 상태로 저장한다")
    void sendPromiseConfirmedNotification_expireSubscription() {
        // given
        Long promiseId = 10L;
        User user = createUser(1L);
        Promise promise = createPromise(promiseId);
        PromiseMember member = PromiseMember.createMember(user, promise, Role.MEMBER);
        PushSubscription subscription = createSubscription(100L, user, "https://push.example.com/expired");
        PromiseConfirmedEvent event = createEvent(promiseId);

        when(promiseMemberRepository.findAllByPromiseId(promiseId)).thenReturn(List.of(member));
        when(pushSubscriptionRepository.findAllByUserIdInAndIsActiveTrue(List.of(1L))).thenReturn(List.of(subscription));
        when(webPushSender.send(eq(subscription), any(String.class))).thenReturn(new WebPushSendResult(false, 410));

        // when
        promiseNotificationService.sendPromiseConfirmedNotification(event);

        // then
        assertThat(subscription.getIsActive()).isFalse();
        assertThat(subscription.getExpiredAt()).isNotNull();

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getSentAt()).isNull();
    }

    @Test
    @DisplayName("약속 멤버가 없으면 구독 조회와 발송을 하지 않는다")
    void sendPromiseConfirmedNotification_noMembers() {
        // given
        Long promiseId = 10L;
        PromiseConfirmedEvent event = createEvent(promiseId);
        when(promiseMemberRepository.findAllByPromiseId(promiseId)).thenReturn(List.of());

        // when
        promiseNotificationService.sendPromiseConfirmedNotification(event);

        // then
        verify(pushSubscriptionRepository, never()).findAllByUserIdInAndIsActiveTrue(any());
        verify(webPushSender, never()).send(any(), any());
        verify(notificationRepository, never()).save(any());
    }

    private PromiseConfirmedEvent createEvent(Long promiseId) {
        return new PromiseConfirmedEvent(
                promiseId,
                "저녁 약속",
                LocalDateTime.of(2026, 5, 11, 19, 0),
                100L,
                "강남역",
                "서울 강남구"
        );
    }

    private PushSubscription createSubscription(Long id, User user, String endpoint) {
        PushSubscription subscription = PushSubscription.create(
                user,
                endpoint,
                "hash-%d".formatted(id),
                "p256dh",
                "auth",
                "test-agent"
        );
        ReflectionTestUtils.setField(subscription, "id", id);
        return subscription;
    }

    private Promise createPromise(Long promiseId) {
        Promise promise = Promise.builder()
                .title("저녁 약속")
                .promisedAt(LocalDateTime.of(2026, 5, 11, 19, 0))
                .category(org.hansung.zigma.domain.promise.entity.Category.MEAL)
                .isMultipleVoting(true)
                .endAt(LocalDateTime.of(2026, 5, 11, 18, 0))
                .status(PromiseStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(promise, "id", promiseId);
        return promise;
    }

    private User createUser(Long userId) {
        User user = User.create(
                UserProvider.LOCAL,
                "provider-id-%d".formatted(userId),
                "test%d@example.com".formatted(userId),
                "tester%d".formatted(userId),
                null
        );
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}

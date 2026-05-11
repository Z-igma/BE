package org.hansung.zigma.domain.notification.service;

import org.hansung.zigma.domain.notification.entity.PushSubscription;
import org.hansung.zigma.domain.notification.repository.PushSubscriptionRepository;
import org.hansung.zigma.domain.notification.web.dto.PushSubscriptionReq;
import org.hansung.zigma.domain.user.entity.User;
import org.hansung.zigma.domain.user.entity.UserProvider;
import org.hansung.zigma.domain.user.exception.UserNotFoundException;
import org.hansung.zigma.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;

    @InjectMocks
    private PushSubscriptionServiceImpl pushSubscriptionService;

    @Test
    @DisplayName("새 웹 푸시 구독이면 endpoint 해시와 키 정보를 저장한다")
    void subscribe_createNewSubscription() {
        // given
        Long userId = 1L;
        User user = createUser(userId);
        PushSubscriptionReq req = createReq("https://push.example.com/new", "p256dh", "auth");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(pushSubscriptionRepository.findByEndpointHash(anyString())).thenReturn(Optional.empty());

        // when
        pushSubscriptionService.subscribe(userId, req, "test-agent");

        // then
        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(pushSubscriptionRepository).save(captor.capture());

        PushSubscription saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getEndpoint()).isEqualTo("https://push.example.com/new");
        assertThat(saved.getEndpointHash()).hasSize(64);
        assertThat(saved.getP256dh()).isEqualTo("p256dh");
        assertThat(saved.getAuth()).isEqualTo("auth");
        assertThat(saved.getUserAgent()).isEqualTo("test-agent");
        assertThat(saved.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("이미 저장된 구독이면 키 정보와 사용자 정보를 갱신한다")
    void subscribe_renewExistingSubscription() {
        // given
        Long userId = 2L;
        User oldUser = createUser(1L);
        User newUser = createUser(userId);
        PushSubscriptionReq req = createReq("https://push.example.com/existing", "new-p256dh", "new-auth");
        PushSubscription existing = PushSubscription.create(
                oldUser,
                "https://push.example.com/existing",
                "hash",
                "old-p256dh",
                "old-auth",
                "old-agent"
        );
        existing.expire(java.time.LocalDateTime.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(newUser));
        when(pushSubscriptionRepository.findByEndpointHash(anyString())).thenReturn(Optional.of(existing));

        // when
        pushSubscriptionService.subscribe(userId, req, "new-agent");

        // then
        assertThat(existing.getUser()).isEqualTo(newUser);
        assertThat(existing.getP256dh()).isEqualTo("new-p256dh");
        assertThat(existing.getAuth()).isEqualTo("new-auth");
        assertThat(existing.getUserAgent()).isEqualTo("new-agent");
        assertThat(existing.getIsActive()).isTrue();
        assertThat(existing.getExpiredAt()).isNull();
        verify(pushSubscriptionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("구독 해제 요청이면 본인 구독만 만료 처리한다")
    void unsubscribe_expireOwnSubscription() {
        // given
        Long userId = 1L;
        User user = createUser(userId);
        PushSubscriptionReq req = createReq("https://push.example.com/remove", "p256dh", "auth");
        PushSubscription subscription = PushSubscription.create(
                user,
                "https://push.example.com/remove",
                "hash",
                "p256dh",
                "auth",
                "agent"
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(pushSubscriptionRepository.findByEndpointHash(anyString())).thenReturn(Optional.of(subscription));

        // when
        pushSubscriptionService.unsubscribe(userId, req);

        // then
        assertThat(subscription.getIsActive()).isFalse();
        assertThat(subscription.getExpiredAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 구독 등록에 실패한다")
    void subscribe_failWhenUserNotFound() {
        // given
        Long userId = 1L;
        PushSubscriptionReq req = createReq("https://push.example.com/new", "p256dh", "auth");
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> pushSubscriptionService.subscribe(userId, req, "agent"))
                .isInstanceOf(UserNotFoundException.class);

        verify(pushSubscriptionRepository, never()).findByEndpointHash(anyString());
        verify(pushSubscriptionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private PushSubscriptionReq createReq(String endpoint, String p256dh, String auth) {
        PushSubscriptionReq req = new PushSubscriptionReq();
        PushSubscriptionReq.Keys keys = new PushSubscriptionReq.Keys();
        ReflectionTestUtils.setField(keys, "p256dh", p256dh);
        ReflectionTestUtils.setField(keys, "auth", auth);
        ReflectionTestUtils.setField(req, "endpoint", endpoint);
        ReflectionTestUtils.setField(req, "keys", keys);
        return req;
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

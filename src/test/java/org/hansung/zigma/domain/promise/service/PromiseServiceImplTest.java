package org.hansung.zigma.domain.promise.service;

import org.hansung.zigma.domain.promise.entity.Promise;
import org.hansung.zigma.domain.promise.entity.PromiseMember;
import org.hansung.zigma.domain.promise.entity.PromiseStatus;
import org.hansung.zigma.domain.promise.entity.Role;
import org.hansung.zigma.domain.promise.exception.PromiseMemberHostOnlyException;
import org.hansung.zigma.domain.promise.repository.PromiseMemberRepository;
import org.hansung.zigma.domain.promise.repository.PromiseRepository;
import org.hansung.zigma.domain.promise.web.dto.PromiseInviteRes;
import org.hansung.zigma.domain.user.entity.User;
import org.hansung.zigma.domain.user.entity.UserProvider;
import org.hansung.zigma.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromiseServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PromiseRepository promiseRepository;

    @Mock
    private PromiseMemberRepository promiseMemberRepository;

    @InjectMocks
    private PromiseServiceImpl promiseService;

    @Test
    @DisplayName("방장이 초대 코드를 생성하면 UUID가 발급되어 응답으로 반환된다")
    void createInviteCode_success() {
        // given: 방장 사용자와 초대 코드가 아직 없는 약속
        Long userId = 1L;
        Long promiseId = 10L;

        User user = createUser(userId);
        Promise promise = createPromise(promiseId, null);
        PromiseMember host = PromiseMember.createMember(user, promise, Role.HOST);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId)).thenReturn(Optional.of(host));

        // when: 초대 코드 생성 API 로직 호출
        PromiseInviteRes res = promiseService.createInviteCode(userId, promiseId);

        // then: promiseId와 UUID 형식의 inviteCode가 응답에 담겨야 함
        System.out.println("inviteRes = " + res);
        assertThat(res.promiseId()).isEqualTo(promiseId);
        assertThat(res.inviteCode()).isNotBlank();
        assertThat(UUID.fromString(res.inviteCode())).isInstanceOf(UUID.class);
        assertThat(promise.getInviteCode()).isEqualTo(res.inviteCode());
    }

    @Test
    @DisplayName("이미 초대 코드가 있으면 새로 만들지 않고 기존 코드를 반환한다")
    void createInviteCode_returnsExistingInviteCode() {
        // given: 약속에 이미 발급된 초대 코드가 있는 상태
        Long userId = 1L;
        Long promiseId = 10L;
        String existingInviteCode = "11111111-2222-3333-4444-555555555555";

        User user = createUser(userId);
        Promise promise = createPromise(promiseId, existingInviteCode);
        PromiseMember host = PromiseMember.createMember(user, promise, Role.HOST);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId)).thenReturn(Optional.of(host));

        // when: 같은 약속에 다시 초대 코드 생성을 요청
        PromiseInviteRes res = promiseService.createInviteCode(userId, promiseId);

        // then: 기존 초대 코드를 그대로 반환해야 함
        assertThat(res.promiseId()).isEqualTo(promiseId);
        assertThat(res.inviteCode()).isEqualTo(existingInviteCode);
    }

    @Test
    @DisplayName("방장이 아닌 참여자는 초대 코드를 생성할 수 없다")
    void createInviteCode_failWhenMemberIsNotHost() {
        // given: 약속 참여자는 맞지만 역할이 MEMBER인 사용자
        Long userId = 1L;
        Long promiseId = 10L;

        User user = createUser(userId);
        Promise promise = createPromise(promiseId, null);
        PromiseMember member = PromiseMember.createMember(user, promise, Role.MEMBER);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(promiseMemberRepository.findByUserIdAndPromiseId(userId, promiseId)).thenReturn(Optional.of(member));

        // when & then: 방장이 아니면 예외가 발생해야 함
        assertThatThrownBy(() -> promiseService.createInviteCode(userId, promiseId))
                .isInstanceOf(PromiseMemberHostOnlyException.class);
    }

    private User createUser(Long userId) {
        User user = User.create(
                UserProvider.LOCAL,
                "provider-id",
                "test@example.com",
                "tester",
                null
        );
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Promise createPromise(Long promiseId, String inviteCode) {
        Promise promise = Promise.builder()
                .title("test promise")
                .promisedAt(LocalDateTime.of(2026, 5, 10, 18, 0))
                .category(org.hansung.zigma.domain.promise.entity.Category.MEAL)
                .isMultipleVoting(true)
                .endAt(LocalDateTime.of(2026, 5, 10, 17, 0))
                .status(PromiseStatus.PENDING)
                .inviteCode(inviteCode)
                .build();
        ReflectionTestUtils.setField(promise, "id", promiseId);
        return promise;
    }
}

package org.hansung.zigma.global.jwt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hansung.zigma.domain.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;

@Getter
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {

    private final User user;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return new ArrayList<>();
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return user.getId().toString();
    }

    // --- 계정 상태 관련 메서드들 (특별한 로직이 없다면 모두 true 반환) ---

    @Override
    public boolean isAccountNonExpired() {
        return true; // 계정이 만료되지 않았음
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // 계정이 잠기지 않았음
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // 자격 증명(비밀번호)이 만료되지 않았음
    }

    @Override
    public boolean isEnabled() {
        return true; // 계정이 활성화됨
    }
}
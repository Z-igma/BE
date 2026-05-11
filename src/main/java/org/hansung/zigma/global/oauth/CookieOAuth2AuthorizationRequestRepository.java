package org.hansung.zigma.global.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hansung.zigma.global.util.CookieUtils;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Component
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "oauth2_auth_request";
    public static final String REDIRECT_TARGET_COOKIE = "redirect_target";
    public static final String TARGET_LOCAL = "local";
    public static final String TARGET_DEPLOY = "deploy";
    private static final int COOKIE_EXPIRE_SECONDS = 180; // 3분

    // 쿠키에서 Authorization Request 조회
    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return CookieUtils.getCookie(request, COOKIE_NAME)
                .map(cookie -> CookieUtils.deserialize(cookie, OAuth2AuthorizationRequest.class))
                .orElse(null);
    }

    // Authorization Request를 쿠키에 저장
    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (authorizationRequest == null) {
            CookieUtils.deleteCookie(request, response, COOKIE_NAME);
            CookieUtils.deleteCookie(request, response, REDIRECT_TARGET_COOKIE);
            return;
        }
        CookieUtils.addCookie(response, COOKIE_NAME,
                CookieUtils.serialize(authorizationRequest), COOKIE_EXPIRE_SECONDS);

        // Referer 헤더로 출발지(local/deploy) 판단 후 쿠키 저장
        // contains 대신 startsWith로 호스트 부분만 검사 (http://attacker.com/localhost 같은 케이스 차단)
        String referer = request.getHeader("Referer");
        boolean isLocal = referer != null
                && (referer.startsWith("http://localhost") || referer.startsWith("https://localhost"));
        String target = isLocal ? TARGET_LOCAL : TARGET_DEPLOY;
        CookieUtils.addCookie(response, REDIRECT_TARGET_COOKIE, target, COOKIE_EXPIRE_SECONDS);
    }

    // 쿠키에서 Authorization Request 꺼내고 삭제
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        CookieUtils.deleteCookie(request, response, COOKIE_NAME);
        // REDIRECT_TARGET_COOKIE는 SuccessHandler에서 사용 후 삭제
        return authorizationRequest;
    }
}

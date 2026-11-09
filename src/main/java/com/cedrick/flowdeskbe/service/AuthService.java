package com.cedrick.flowdeskbe.service;

import com.cedrick.flowdeskbe.dto.LoginRequestDto;
import com.cedrick.flowdeskbe.dto.UserResponse;
import com.cedrick.flowdeskbe.mapper.RequestMapper;
import com.cedrick.flowdeskbe.security.AuthUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    /** Authenticates, rotates the session id (anti session-fixation) and stores the context in the session. */
    public UserResponse login(LoginRequestDto dto, HttpServletRequest request, HttpServletResponse response) {
        // Throws BadCredentialsException (-> generic 401) on failure.
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(dto.email().trim(), dto.password()));

        if (request.getSession(false) != null) {
            request.changeSessionId();
        } else {
            request.getSession(true);
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return RequestMapper.toUserResponse((AuthUserPrincipal) auth.getPrincipal());
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}

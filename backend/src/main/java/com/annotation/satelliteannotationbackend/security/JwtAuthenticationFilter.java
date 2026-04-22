package com.annotation.satelliteannotationbackend.security;

import com.annotation.satelliteannotationbackend.entity.User;
import com.annotation.satelliteannotationbackend.service.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        logger.debug("[JWT Filter] Authorization header: {}", authHeader != null ? "present" : "null");
        logger.debug("[JWT Filter] Header value length: {}", authHeader != null ? authHeader.length() : 0);
        if (authHeader != null) {
            logger.debug("[JWT Filter] Header starts with 'Bearer ': {}", authHeader.startsWith("Bearer "));
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String jwt = authHeader.substring(7);
            String username = jwtUtil.extractUsername(jwt);
            logger.debug("[JWT Filter] Extracted username: {}", username);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                logger.debug("[JWT Filter] Loaded UserDetails: {}", userDetails.getClass().getName());

                if (jwtUtil.validateToken(jwt, userDetails)) {
                    // 从 UserDetailsServiceImpl 中获取 User 实体（如果可能）
                    User userEntity = null;
                    if (userDetails instanceof com.annotation.satelliteannotationbackend.service.UserDetailsImpl) {
                        userEntity = ((com.annotation.satelliteannotationbackend.service.UserDetailsImpl) userDetails).getUser();
                    }
                    logger.debug("[JWT Filter] User entity: {}", userEntity != null ? "found" : "null");

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userEntity != null ? userEntity : userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            logger.error("JWT authentication error: {}", e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }
}

package com.triasoft.socialpublisher.security;

import com.triasoft.socialpublisher.entity.SocialClient;
import com.triasoft.socialpublisher.repository.SocialClientRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Validates the Bearer token issued by {@code OAuthController}, and — if valid — sets the
 * resolved {@link SocialClient} itself as the Authentication principal, so controllers can take
 * it directly via {@code @AuthenticationPrincipal SocialClient client}. An invalid/missing/
 * expired token simply leaves the request unauthenticated; Spring Security's default entry point
 * then returns 401 for any endpoint requiring authentication.
 */
@Component
@RequiredArgsConstructor
public class ClientJwtAuthenticationFilter extends OncePerRequestFilter {

    private final ClientTokenService clientTokenService;
    private final SocialClientRepository clientRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            Optional<SocialClient> client = clientTokenService.extractClientDbId(token)
                    .flatMap(clientRepository::findById)
                    .filter(SocialClient::isActive);
            client.ifPresent(c -> SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(c, null, List.of())));
        }
        filterChain.doFilter(request, response);
    }
}

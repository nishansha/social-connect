package com.triasoft.socialconnect.security;

import com.triasoft.socialconnect.config.JwtProperties;
import com.triasoft.socialconnect.entity.SocialClient;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/** Issues and verifies this service's own OAuth2 client-credentials access tokens (HS256 JWT). */
@Service
public class ClientTokenService {

    private static final String CLIENT_DB_ID_CLAIM = "cid";

    private final SecretKey secretKey;
    private final JwtProperties jwtProperties;

    public ClientTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueToken(SocialClient client) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.expirationMs());
        return Jwts.builder()
                .subject(client.getClientId())
                .claim(CLIENT_DB_ID_CLAIM, client.getId())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public long expirationSeconds() {
        return jwtProperties.expirationMs() / 1000;
    }

    /** Returns the client's database id if the token is well-formed and unexpired; empty otherwise (never throws to the caller). */
    public Optional<Long> extractClientDbId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.ofNullable(claims.get(CLIENT_DB_ID_CLAIM, Long.class));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

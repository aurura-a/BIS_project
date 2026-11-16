package kz.gymbooking.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import kz.gymbooking.dto.RegisterRequest;
import kz.gymbooking.dto.TokenPairResponse;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.repository.UserRepository;
import kz.gymbooking.repository.UserRepository.UserRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(30);
    private static final int ACCESS_TOKEN_EXPIRES_IN = (int) ACCESS_TOKEN_LIFETIME.toSeconds();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder refreshJwtDecoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                       @Qualifier("refreshJwtDecoder") JwtDecoder refreshJwtDecoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.refreshJwtDecoder = refreshJwtDecoder;
    }

    public TokenPairResponse register(RegisterRequest request) {
        try {
            UserRecord user = userRepository.create(request.fullName(), request.email(), request.phone(),
                    passwordEncoder.encode(request.password()), "CLIENT");
            return createTokenPair(user);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already registered");
        }
    }

    public TokenPairResponse login(String email, String password) {
        UserRecord user = userRepository.findByEmail(email)
                .orElseThrow(this::unauthorized);
        if (user.passwordHash() == null || !passwordEncoder.matches(password, user.passwordHash())) {
            throw unauthorized();
        }
        return createTokenPair(user);
    }

    public TokenPairResponse refresh(String refreshToken) {
        Jwt token;
        try {
            token = refreshJwtDecoder.decode(refreshToken);
        } catch (JwtException exception) {
            throw unauthorized();
        }
        if (!"refresh".equals(token.getClaimAsString("token_type"))) {
            throw unauthorized();
        }

        UserRecord user = findTokenUser(token);
        Object tokenVersionClaim = token.getClaims().get("token_version");
        if (!(tokenVersionClaim instanceof Number tokenVersionNumber)
                || user.tokenVersion() != tokenVersionNumber.intValue()
                || !userRepository.rotateTokenVersion(user.id(), tokenVersionNumber.intValue())) {
            throw unauthorized();
        }
        return createTokenPair(userRepository.findById(user.id()).orElseThrow(this::unauthorized));
    }

    public void logout(long userId) {
        userRepository.revokeTokens(userId);
    }

    private UserRecord findTokenUser(Jwt token) {
        try {
            long userId = Long.parseLong(token.getSubject());
            return userRepository.findById(userId).orElseThrow(this::unauthorized);
        } catch (NumberFormatException exception) {
            throw unauthorized();
        }
    }

    private TokenPairResponse createTokenPair(UserRecord user) {
        return new TokenPairResponse(
                createToken(user, "access", ACCESS_TOKEN_LIFETIME),
                createToken(user, "refresh", REFRESH_TOKEN_LIFETIME),
                ACCESS_TOKEN_EXPIRES_IN,
                new TokenPairResponse.UserSummary(
                        user.id(), user.email(), user.fullName(), user.phone(), user.role()));
    }

    private String createToken(UserRecord user, String type, Duration lifetime) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("gym-booking")
                .subject(Long.toString(user.id()))
                .issuedAt(now)
                .expiresAt(now.plus(lifetime))
                .id(UUID.randomUUID().toString())
                .claim("token_type", type)
                .claim("token_version", user.tokenVersion())
                .claim("role", user.role())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Email or password is invalid");
    }
}

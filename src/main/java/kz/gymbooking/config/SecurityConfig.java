package kz.gymbooking.config;

import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecretKey jwtSecretKey(@Value("${app.jwt.secret}") String encodedSecret) {
        if (encodedSecret.isBlank()) {
            throw new IllegalStateException("Set JWT_SECRET to a base64-encoded key of at least 32 bytes");
        }
        byte[] key = Base64.getDecoder().decode(encodedSecret);
        if (key.length < 32) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new com.nimbusds.jose.proc.ImmutableSecret<>(jwtSecretKey));
    }

    @Bean("accessJwtDecoder")
    JwtDecoder accessJwtDecoder(SecretKey jwtSecretKey, JdbcTemplate jdbc) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> accessTokenValidator = jwt -> {
            if (!"access".equals(jwt.getClaimAsString("token_type"))) {
                return invalidToken("Not an access token");
            }
            Long userId;
            Integer tokenVersion;
            try {
                userId = Long.valueOf(jwt.getSubject());
                Object tokenVersionClaim = jwt.getClaims().get("token_version");
                if (!(tokenVersionClaim instanceof Number tokenVersionNumber)) {
                    return invalidToken("Invalid access token claims");
                }
                tokenVersion = tokenVersionNumber.intValue();
            } catch (NumberFormatException exception) {
                return invalidToken("Invalid access token claims");
            }
            Integer currentVersion = jdbc.query("""
                    SELECT token_version FROM users WHERE id = ?
                    """, result -> result.next() ? result.getInt(1) : null, userId);
            return tokenVersion.equals(currentVersion)
                    ? OAuth2TokenValidatorResult.success()
                    : invalidToken("Access token has been revoked");
        };
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), accessTokenValidator));
        return decoder;
    }

    @Bean("refreshJwtDecoder")
    JwtDecoder refreshJwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))),
                jwt.getSubject());
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                             @org.springframework.beans.factory.annotation.Qualifier("accessJwtDecoder")
                                             JwtDecoder accessJwtDecoder,
                                             Converter<Jwt, ? extends AbstractAuthenticationToken> converter)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/auth/register", "/auth/login", "/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/classes", "/classes/*", "/membership-plans").permitAll()
                        .requestMatchers(HttpMethod.GET, "/classes/*/bookings")
                        .hasAnyRole("TRAINER", "STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/classes", "/classes/*/cancel")
                        .hasAnyRole("TRAINER", "STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/classes/*")
                        .hasAnyRole("TRAINER", "STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/users/*/role").hasRole("ADMIN")
                        .requestMatchers("/auth/logout").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(accessJwtDecoder).jwtAuthenticationConverter(converter)))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }

    private static OAuth2TokenValidatorResult invalidToken(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}

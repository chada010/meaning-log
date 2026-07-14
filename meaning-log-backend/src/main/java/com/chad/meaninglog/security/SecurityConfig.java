package com.chad.meaninglog.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

import static com.chad.meaninglog.web.WebConstants.FREEDNS_ORIGIN;
import static com.chad.meaninglog.web.WebConstants.FREEDNS_WWW_ORIGIN;
import static com.chad.meaninglog.web.WebConstants.LOOPBACK_FRONTEND_ORIGIN;
import static com.chad.meaninglog.web.WebConstants.LOCAL_FRONTEND_ORIGIN;
import static com.chad.meaninglog.web.WebConstants.PRIMARY_VERCEL_ORIGIN;
import static com.chad.meaninglog.web.WebConstants.VERCEL_ORIGIN_PATTERN;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/reset-password", "/api/auth/send-code").permitAll()
                        .requestMatchers("/api/trial/**").permitAll()
                        // TODO: 生产环境需限制 Swagger 与 Actuator 访问（Nginx 白名单或独立 basic-auth）
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/doc.html", "/webjars/**", "/favicon.ico").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                LOCAL_FRONTEND_ORIGIN,
                LOOPBACK_FRONTEND_ORIGIN,
                PRIMARY_VERCEL_ORIGIN,
                FREEDNS_ORIGIN,
                FREEDNS_WWW_ORIGIN
        ));
        configuration.setAllowedOriginPatterns(List.of(VERCEL_ORIGIN_PATTERN));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        configuration.setExposedHeaders(List.of(HttpHeaders.AUTHORIZATION));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Map.of("message", "请先登录"));
        };
    }
}

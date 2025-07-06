package com.callibrity.mocapi.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(ApplicationProps.class)
@RequiredArgsConstructor
public class ApplicationConfig {
    private static final String API_PATTERN = "/api/**";

    private final ApplicationProps props;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NonNull CorsRegistry registry) {
                registry.addMapping(API_PATTERN).combine(props.getCors());
            }
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults()) // Allow CORS preflight requests
                .csrf(csrf -> csrf.ignoringRequestMatchers(API_PATTERN)) // Disable CSRF for API endpoints
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .authorizeHttpRequests(authz -> {
                    authz.requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll(); // Allow all actuator endpoints
                    authz.requestMatchers(API_PATTERN).fullyAuthenticated(); // Require authentication for API endpoints
                    authz.anyRequest().denyAll(); // Deny all other requests
                })
                .build();
    }
}

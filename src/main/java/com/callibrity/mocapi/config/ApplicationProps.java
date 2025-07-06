package com.callibrity.mocapi.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.cors.CorsConfiguration;

@Data
@ConfigurationProperties(prefix = "mocapi")
public class ApplicationProps {
    private final CorsConfiguration cors = new CorsConfiguration();
}

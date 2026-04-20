package com.x8ing.mtl.server.mtlserver.web.global;

import org.springframework.context.annotation.Configuration;

@Configuration
public class CorsConfiguration {

    // overruled by Spring Security config
//    @Bean
//    public WebMvcConfigurer corsConfigurer() {
//        return new WebMvcConfigurer() {
//            @Override
//            public void addCorsMappings(CorsRegistry registry) {
//                registry.addMapping("/**") // This allows all endpoints in your API
//                        .allowedOriginPatterns("localhost:*", "localhost:8080", "localhost:5173", "mindalyze.hopto.org:49040") // You can specify the allowed origins here, or use '*' to allow all origins
//                        .allowedMethods("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS")
//                        .allowedHeaders("*") // You can specify the allowed headers here, or use '*' to allow all headers
//                        .allowCredentials(true)
//                        .maxAge(3600);
//            }
//        };
//    }
}
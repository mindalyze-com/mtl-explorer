package com.x8ing.mtl.server.mtlserver.web.global.httpcache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CacheableResponse {
    int durationInSeconds() default 60;  // Default cache duration in seconds.
}
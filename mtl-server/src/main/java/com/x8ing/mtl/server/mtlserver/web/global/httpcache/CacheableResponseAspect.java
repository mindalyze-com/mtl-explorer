package com.x8ing.mtl.server.mtlserver.web.global.httpcache;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class CacheableResponseAspect {

    @Around("@annotation(cacheableResponse)")
    public Object handleCacheable(ProceedingJoinPoint pjp, CacheableResponse cacheableResponse) throws Throwable {
        ResponseEntity<?> responseEntity = (ResponseEntity<?>) pjp.proceed();
        return ResponseEntity.status(responseEntity.getStatusCode())
                .cacheControl(CacheControl.maxAge(cacheableResponse.durationInSeconds(), TimeUnit.SECONDS))
                .headers(responseEntity.getHeaders())
                .body(responseEntity.getBody());
    }
}

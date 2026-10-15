package com.x8ing.mtl.server.mtlserver.web.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSecurityUserDetailsTest {

    @Test
    void configuredUserHasEncodedPasswordWithoutSampleApplicationWarning() {
        WebSecurityConfig configuration = new WebSecurityConfig(null, null);
        ReflectionTestUtils.setField(configuration, "userName", "monitor-test");
        ReflectionTestUtils.setField(configuration, "userPwd", "synthetic-test-password");

        Logger userLogger = (Logger) LoggerFactory.getLogger(User.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        userLogger.addAppender(appender);
        try {
            UserDetails user = configuration.userDetailsService().loadUserByUsername("monitor-test");
            var passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

            assertTrue(user.getPassword().startsWith("{bcrypt}"));
            assertTrue(passwordEncoder.matches("synthetic-test-password", user.getPassword()));
            assertFalse(passwordEncoder.matches("incorrect-password", user.getPassword()));
            assertTrue(user.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_USER".equals(authority.getAuthority())));
            assertFalse(appender.list.stream().anyMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN)),
                    "Configured credentials must not trigger the sample-application password warning");
        } finally {
            userLogger.detachAppender(appender);
            appender.stop();
        }
    }
}

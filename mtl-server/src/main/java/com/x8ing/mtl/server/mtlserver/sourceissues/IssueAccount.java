package com.x8ing.mtl.server.mtlserver.sourceissues;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Locale;

public final class IssueAccount {
    private IssueAccount() {
    }

    /** The authenticated UserDetails principal supplies the canonical configured login. */
    public static String current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated() ? ""
                : authentication.getName().toLowerCase(Locale.ROOT);
    }
}

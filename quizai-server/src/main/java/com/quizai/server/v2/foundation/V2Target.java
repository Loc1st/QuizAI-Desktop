package com.quizai.server.v2.foundation;

import java.net.InetAddress;
import java.net.URI;
import java.util.Set;

/** Validate before constructing a datasource; never accepts legacy or administrative targets. */
public final class V2Target {
    private V2Target() { }

    public static void validate(String profile, String url, String username, String password) {
        if (profile == null || !Set.of("v2-dev", "v2-test").contains(profile)) fail("PROFILE_INVALID");
        String database = "quizai_" + profile.replace('-', '_');
        if (url == null || url.isBlank()) fail("URL_MISSING");
        if (username == null || username.isBlank()) fail("USERNAME_MISSING");
        if (!database.equals(username)) fail("USERNAME_MISMATCH");
        if (password == null || password.isBlank()) fail("PASSWORD_MISSING");
        if (password.contains("${")) fail("PASSWORD_UNRESOLVED");
        if (!url.startsWith("jdbc:postgresql://")) fail("URL_FORMAT_INVALID");
        try {
            URI target = URI.create(url.substring(5));
            if (!Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(target.getHost())
                    || target.getPort() != 5432 || !("/" + database).equals(target.getRawPath())
                    || target.getRawUserInfo() != null || target.getRawQuery() != null || target.getRawFragment() != null) fail("URL_TARGET_INVALID");
            for (InetAddress address : InetAddress.getAllByName(target.getHost().replace("[", "").replace("]", ""))) {
                if (!address.isLoopbackAddress()) fail("URL_TARGET_INVALID");
            }
        } catch (Exception ignored) { fail("URL_TARGET_INVALID"); }
    }

    private static void fail(String reason) {
        throw new IllegalStateException("V2_TARGET_REJECTED[" + reason + "]: dedicated local V2 role/database required; credentials withheld");
    }
}

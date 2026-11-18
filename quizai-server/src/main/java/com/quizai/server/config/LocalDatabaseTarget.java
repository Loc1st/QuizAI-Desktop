package com.quizai.server.config;

import java.net.InetAddress;
import java.net.URI;
import java.util.Set;

/** Current authorization is restricted to these two databases on the verified local PG18 instance. */
public final class LocalDatabaseTarget {
    private LocalDatabaseTarget() { }

    public static void validate(String profile, String url, String username, String password) {
        if (!Set.of("dev", "test").contains(profile)) {
            throw new IllegalStateException("Only local dev/test datasource profiles are authorized");
        }
        String database = profile.equals("dev") ? "quizai_dev" : "quizai_test";
        if (url == null || username == null || password == null || password.isBlank()
                || password.contains("${") || !username.equals(database) || !url.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("Missing or invalid dedicated " + profile + " database environment");
        }
        try {
            URI target = URI.create(url.substring("jdbc:".length()));
            if (target.getHost() == null || !Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(target.getHost())
                    || target.getPort() != 5432 || !("/" + database).equals(target.getPath())
                    || target.getRawQuery() != null || target.getRawUserInfo() != null || target.getRawFragment() != null) {
                throw new IllegalArgumentException();
            }
            for (InetAddress address : InetAddress.getAllByName(target.getHost().replace("[", "").replace("]", ""))) {
                if (!address.isLoopbackAddress()) { throw new IllegalArgumentException(); }
            }
        } catch (Exception ignored) {
            throw new IllegalStateException("Datasource must target only loopback:5432/" + database + "; value withheld");
        }
    }
}

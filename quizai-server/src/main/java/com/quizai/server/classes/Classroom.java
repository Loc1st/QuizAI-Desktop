package com.quizai.server.classes;

import java.time.Instant;
import java.util.UUID;

/** Internal legacy foundation row; never serialize the stored join-code digest. */
public record Classroom(UUID id, UUID teacherId, String name, String joinCodeDigest,
                        boolean joinEnabled, String status, Instant createdAt) { }

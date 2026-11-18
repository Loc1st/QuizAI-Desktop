package com.quizai.server.classes;

import java.time.Instant;
import java.util.UUID;

public record JoinedClassView(UUID id, UUID teacherId, String name, String status,
                              boolean joinEnabled, UUID membershipId, Instant joinedAt) { }

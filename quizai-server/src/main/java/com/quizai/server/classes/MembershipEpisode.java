package com.quizai.server.classes;

import java.time.Instant;
import java.util.UUID;

public record MembershipEpisode(UUID id, UUID classId, UUID studentId, Instant joinedAt,
                                Instant leftAt, Instant createdAt) { }

package com.quizai.server.classes;

import java.time.Instant;
import java.util.UUID;

public record ClassMemberView(UUID membershipId, UUID studentId, String email, Instant joinedAt) { }

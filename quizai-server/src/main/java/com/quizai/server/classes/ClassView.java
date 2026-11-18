package com.quizai.server.classes;

import java.util.UUID;

public record ClassView(UUID id, UUID teacherId, String name, String status,
                        boolean joinEnabled, String joinCode) { }

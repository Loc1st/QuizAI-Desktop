package com.quizai.server.catalog;

import java.util.UUID;

public record TopicView(UUID id, UUID subjectId, String code, String name) { }

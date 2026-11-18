package com.quizai.server.catalog;

import java.util.UUID;

public record SubjectView(UUID id, String code, String name, int catalogVersion) { }

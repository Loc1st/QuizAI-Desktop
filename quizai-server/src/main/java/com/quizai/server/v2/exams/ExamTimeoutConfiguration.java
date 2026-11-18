package com.quizai.server.v2.exams;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@Profile({"v2-dev", "v2-test"})
@EnableScheduling
public class ExamTimeoutConfiguration {}

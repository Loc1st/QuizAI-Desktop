# QuizAI Desktop — Coding agent instructions

1. Before each task, read `docs/PROJECT_DECISIONS_v7.md` and `docs/REQUIREMENTS_BASELINE_v1.0.md` completely. Higher-numbered decisions and Round 7 take precedence over historical open items.
2. Do not add features outside the accepted MVP. DEFERRED/NOT INCLUDED items remain outside scope.
3. Do not change ACCEPTED requirements. Business changes require a Change Request approved by the user.
4. Use Java 21 and Gradle multi-module with Groovy DSL. Preserve the Gradle Wrapper unless a change is necessary and justified.
5. Client and Admin are JavaFX + CSS desktop applications; keep their FXML, controllers and navigation in their respective modules.
6. The backend uses Spring Boot. Add dependencies only when needed for the requested task.
7. Implement only the task currently requested. Do not introduce speculative business logic or empty Java classes.
8. Never hardcode secrets or put API keys, JPA entities, repositories or server internals in `quizai-common`.
9. Do not proceed automatically to the next task. TCP demonstrations must remain LAN/localhost only.
10. Always run the applicable Gradle build/checks and report actual results, including startup checks and environmental limitations. Never claim a GUI ran without verifying it.

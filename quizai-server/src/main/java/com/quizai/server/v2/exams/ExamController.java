package com.quizai.server.v2.exams;

import com.quizai.server.security.SessionPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.quizai.server.v2.exams.ExamDtos.*;

@RestController
@Profile({"v2-dev", "v2-test"})
@RequestMapping("/api/v2")
public class ExamController {
    private final TeacherExamService teacher;
    private final StudentExamService student;
    public ExamController(TeacherExamService teacher, StudentExamService student) { this.teacher=teacher; this.student=student; }

    @GetMapping("/teacher/banks") public List<Bank> banks(@AuthenticationPrincipal SessionPrincipal p) { return teacher.banks(p); }
    @PostMapping("/teacher/banks") public Bank createBank(@AuthenticationPrincipal SessionPrincipal p,@RequestBody BankRequest r) { return teacher.createBank(p,r); }
    @GetMapping("/teacher/questions") public List<Question> questions(@AuthenticationPrincipal SessionPrincipal p,
            @RequestParam(required=false) UUID bankId,@RequestParam(required=false) UUID subjectId,
            @RequestParam(required=false) UUID topicId,@RequestParam(required=false) String difficulty) {
        return teacher.questions(p,bankId,subjectId,topicId,difficulty);
    }
    @PostMapping("/teacher/questions") public Question createQuestion(@AuthenticationPrincipal SessionPrincipal p,@RequestBody QuestionRequest r) { return teacher.createQuestion(p,r); }
    @GetMapping("/teacher/questions/{id}/versions") public List<Question> versions(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id) { return teacher.versions(p,id); }
    @PostMapping("/teacher/questions/{id}/versions") public Question newVersion(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@RequestBody QuestionRequest r) { return teacher.newVersion(p,id,r); }
    @PostMapping("/teacher/questions/{id}/versions/{versionId}/approve") public Question approve(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@PathVariable UUID versionId) { return teacher.approve(p,id,versionId); }
    @GetMapping("/teacher/quizzes") public List<Quiz> quizzes(@AuthenticationPrincipal SessionPrincipal p) { return teacher.quizzes(p); }
    @GetMapping("/teacher/quizzes/{id}") public Quiz quiz(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id) { return teacher.getQuiz(p,id); }
    @PostMapping("/teacher/quizzes") public Quiz createQuiz(@AuthenticationPrincipal SessionPrincipal p,@RequestBody QuizRequest r) { return teacher.createQuiz(p,r); }
    @PutMapping("/teacher/quizzes/{id}") public Quiz updateQuiz(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@RequestBody QuizRequest r) { return teacher.updateQuiz(p,id,r); }
    @GetMapping("/teacher/assignments") public List<Assignment> assignments(@AuthenticationPrincipal SessionPrincipal p) { return teacher.assignments(p); }
    @PostMapping("/teacher/assignments") public Assignment createAssignment(@AuthenticationPrincipal SessionPrincipal p,@RequestBody AssignmentRequest r) { return teacher.createAssignment(p,r); }
    @PostMapping("/teacher/assignments/{id}/publish") public Assignment publish(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id) { return teacher.publish(p,id); }
    @PutMapping("/teacher/assignments/{id}/releases") public Assignment releases(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@RequestBody ReleasesRequest r) { return teacher.releases(p,id,r); }
    @GetMapping("/teacher/assignments/{id}/results") public List<StudentResult> results(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id) { return teacher.results(p,id); }
    @GetMapping("/student/assignments") public List<StudentAssignment> studentAssignments(@AuthenticationPrincipal SessionPrincipal p) { return student.assignments(p); }
    @PostMapping("/student/assignments/{id}/attempts") public Attempt start(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@RequestBody StartRequest r) { return student.start(p,id,r); }
    @GetMapping("/student/assignments/{id}/attempts") public List<Attempt> history(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id) { return student.history(p,id); }
    @GetMapping("/student/assignments/{id}/review") public List<AttemptQuestion> review(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id) { return student.review(p,id); }
    @GetMapping("/student/attempts/{id}") public Attempt attempt(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id) { return student.get(p,id); }
    @PutMapping("/student/attempts/{id}/answers/{questionId}") public SavedAnswer save(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@PathVariable UUID questionId,@RequestBody AnswerRequest r) { return student.save(p,id,questionId,r); }
    @PostMapping("/student/attempts/{id}/submit") public Attempt submit(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id,@RequestBody SubmitRequest r) { return student.submit(p,id,r); }
}

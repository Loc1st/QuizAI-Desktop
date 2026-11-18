-- Read-model SQL functions used by secure Server services. Not an authorization replacement.
-- Score source: effective candidate PER attempt; max only ACROSS eligible attempts.
CREATE VIEW quizai_assignment_final_scores AS
SELECT r.id AS recipient_id,
       best.attempt_id AS best_attempt_id,
       best.earned_points,
       best.possible_points,
       best.normalized_score
FROM assignment_recipients r
LEFT JOIN LATERAL (
    SELECT t.id AS attempt_id, s.earned_points, s.possible_points, s.normalized_score
    FROM attempts t
    JOIN submission_scores s ON s.candidate_id=t.effective_candidate_id
    WHERE t.recipient_id=r.id AND t.phase='CLOSED'
    ORDER BY s.normalized_score DESC, t.attempt_no ASC
    LIMIT 1
) best ON TRUE;

CREATE FUNCTION quizai_calculate_candidate_score(p_candidate_id UUID)
RETURNS TABLE(earned_points INTEGER,possible_points INTEGER,normalized_score NUMERIC(12,8))
LANGUAGE sql STABLE AS $$
 SELECT (count(*) FILTER (WHERE o.is_correct IS TRUE))::INTEGER,
        count(*)::INTEGER,
        round((count(*) FILTER (WHERE o.is_correct IS TRUE))::NUMERIC /
              nullif(count(*),0),8)::NUMERIC(12,8)
 FROM submission_candidates c
 JOIN attempts t ON t.id=c.attempt_id
 JOIN assignment_recipients r ON r.id=t.recipient_id
 JOIN assignment_question_snapshots q ON q.assignment_id=r.assignment_id
 LEFT JOIN submission_candidate_answers ca ON ca.candidate_id=c.id AND ca.question_snapshot_id=q.id
 LEFT JOIN assignment_snapshot_options o ON o.id=ca.selected_option_id AND o.question_snapshot_id=q.id
 WHERE c.id=p_candidate_id
 HAVING count(*)>0;
$$;

CREATE FUNCTION quizai_can_view_score(p_recipient_id UUID,p_student_id UUID,p_at TIMESTAMPTZ)
RETURNS BOOLEAN LANGUAGE sql STABLE AS $$
 SELECT EXISTS(
  SELECT 1 FROM assignment_recipients r JOIN assignments a ON a.id=r.assignment_id
  WHERE r.id=p_recipient_id AND r.student_id=p_student_id
    AND a.score_release_at IS NOT NULL AND a.score_release_at<=p_at
    AND EXISTS(
      SELECT 1 FROM attempts t JOIN submission_scores s ON s.candidate_id=t.effective_candidate_id
      WHERE t.recipient_id=r.id AND t.phase='CLOSED'
    )
 );
$$;

CREATE FUNCTION quizai_can_view_answers(p_recipient_id UUID,p_student_id UUID,p_at TIMESTAMPTZ)
RETURNS BOOLEAN LANGUAGE sql STABLE AS $$
 SELECT EXISTS (
  SELECT 1 FROM assignment_recipients r JOIN assignments a ON a.id=r.assignment_id
  WHERE r.id=p_recipient_id AND r.student_id=p_student_id
    AND a.answer_release_at IS NOT NULL AND a.answer_release_at<=p_at
    AND (a.answer_release_mode='AFTER_VALID_SUBMISSION' OR p_at>=a.closes_at)
    AND EXISTS(SELECT 1 FROM attempts t WHERE t.recipient_id=r.id
               AND t.phase='CLOSED' AND t.effective_candidate_id IS NOT NULL)
    AND NOT EXISTS(SELECT 1 FROM attempts t WHERE t.recipient_id=r.id
                   AND t.phase='IN_PROGRESS')
 );
$$;

-- Aggregate teacher-only stats, filtered by COMPLETED within membership episodes.
-- EXISTS avoids double counting when teacher owns two overlapping classes.
CREATE FUNCTION quizai_teacher_practice_aggregate(
 p_teacher_id UUID,p_student_id UUID,p_from TIMESTAMPTZ,p_until TIMESTAMPTZ)
RETURNS TABLE(completed_sessions BIGINT,total_correct BIGINT,total_questions BIGINT)
LANGUAGE sql STABLE AS $$
 SELECT count(*), coalesce(sum(ps.correct_count),0)::BIGINT,coalesce(sum(ps.total_questions),0)::BIGINT
 FROM practice_sessions ps
 WHERE ps.student_id=p_student_id AND ps.status='COMPLETED'
   AND ps.finished_at>=p_from AND ps.finished_at<p_until
   AND EXISTS (
      SELECT 1 FROM class_members m JOIN classes c ON c.id=m.class_id
      JOIN users teacher ON teacher.id=c.teacher_id AND teacher.role='TEACHER'
      WHERE m.student_id=ps.student_id AND c.teacher_id=p_teacher_id
        AND m.joined_at<=ps.finished_at AND (m.left_at IS NULL OR ps.finished_at<m.left_at)
   );
$$;

-- Record observed entitlement before a state transition can hide it (for example,
-- starting attempt 2 temporarily blocks answer access after a completed attempt 1).
-- The actual server observation time is recorded, never a client time or backdated receipt.
CREATE FUNCTION quizai_record_current_publications(p_assignment_id UUID)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE assignment_row assignments%ROWTYPE; observed_at TIMESTAMPTZ; witness UUID;
BEGIN
 SELECT * INTO assignment_row FROM assignments WHERE id=p_assignment_id FOR UPDATE;
 IF NOT FOUND THEN RETURN; END IF;
 observed_at:=clock_timestamp();
 SELECT r.id INTO witness FROM assignment_recipients r
 WHERE r.assignment_id=p_assignment_id AND quizai_can_view_score(r.id,r.student_id,observed_at)
 ORDER BY r.id LIMIT 1;
 IF witness IS NOT NULL THEN
   INSERT INTO assignment_publication_events(id,assignment_id,release_kind,first_available_at,
     witness_recipient_id,policy_revision,source_event)
   VALUES(gen_random_uuid(),p_assignment_id,'SCORE',observed_at,witness,assignment_row.policy_revision,'FIRST_AVAILABLE')
   ON CONFLICT ON CONSTRAINT uq_assignment_publication_kind DO NOTHING;
 END IF;
 SELECT r.id INTO witness FROM assignment_recipients r
 WHERE r.assignment_id=p_assignment_id AND quizai_can_view_answers(r.id,r.student_id,observed_at)
 ORDER BY r.id LIMIT 1;
 IF witness IS NOT NULL THEN
   INSERT INTO assignment_publication_events(id,assignment_id,release_kind,first_available_at,
     witness_recipient_id,policy_revision,source_event)
   VALUES(gen_random_uuid(),p_assignment_id,'ANSWER',observed_at,witness,assignment_row.policy_revision,'FIRST_AVAILABLE')
   ON CONFLICT ON CONSTRAINT uq_assignment_publication_kind DO NOTHING;
 END IF;
END $$;

CREATE FUNCTION quizai_observe_attempt_publications() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE assignment_uuid UUID;
BEGIN
 SELECT assignment_id INTO assignment_uuid FROM assignment_recipients WHERE id=NEW.recipient_id;
 IF assignment_uuid IS NOT NULL THEN PERFORM quizai_record_current_publications(assignment_uuid); END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_publication_before_attempt BEFORE INSERT OR UPDATE OF phase,effective_candidate_id ON attempts
FOR EACH ROW EXECUTE FUNCTION quizai_observe_attempt_publications();
CREATE TRIGGER trg_publication_after_attempt AFTER INSERT OR UPDATE OF phase,effective_candidate_id ON attempts
FOR EACH ROW EXECUTE FUNCTION quizai_observe_attempt_publications();

CREATE FUNCTION quizai_observe_assignment_publications() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 PERFORM quizai_record_current_publications(NEW.id);
 RETURN NEW;
END $$;
CREATE TRIGGER trg_publication_after_release AFTER UPDATE OF score_release_at,answer_release_at,answer_release_mode ON assignments
FOR EACH ROW EXECUTE FUNCTION quizai_observe_assignment_publications();

-- Guard against retracting *already available* score/answer policies, even without a GET.
-- Services must acquire assignment -> recipient -> attempt locks before mutations;
-- these fallback triggers do not replace the approved transactional service lock order.
CREATE FUNCTION quizai_guard_release_non_retraction() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE score_available BOOLEAN; answer_available BOOLEAN;
BEGIN
 IF NEW.score_release_at IS NOT DISTINCT FROM OLD.score_release_at
    AND NEW.answer_release_at IS NOT DISTINCT FROM OLD.answer_release_at
    AND NEW.answer_release_mode IS NOT DISTINCT FROM OLD.answer_release_mode THEN
      RETURN NEW;
 END IF;
 PERFORM quizai_record_current_publications(OLD.id);
 score_available := EXISTS(
    SELECT 1 FROM assignment_publication_events e WHERE e.assignment_id=OLD.id AND e.release_kind='SCORE'
 ) OR (
    OLD.score_release_at IS NOT NULL AND OLD.score_release_at<=clock_timestamp() AND EXISTS (
      SELECT 1 FROM assignment_recipients r JOIN attempts t ON t.recipient_id=r.id
      JOIN submission_scores s ON s.candidate_id=t.effective_candidate_id
      WHERE r.assignment_id=OLD.id AND t.phase='CLOSED'
    )
 );
 answer_available := EXISTS(
    SELECT 1 FROM assignment_publication_events e WHERE e.assignment_id=OLD.id AND e.release_kind='ANSWER'
 ) OR (
    OLD.answer_release_at IS NOT NULL AND OLD.answer_release_at<=clock_timestamp()
    AND (OLD.answer_release_mode='AFTER_VALID_SUBMISSION' OR clock_timestamp()>=OLD.closes_at)
    AND EXISTS (
      SELECT 1 FROM assignment_recipients r JOIN attempts t ON t.recipient_id=r.id
      WHERE r.assignment_id=OLD.id AND t.phase='CLOSED' AND t.effective_candidate_id IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM attempts running WHERE running.recipient_id=r.id
                        AND running.phase='IN_PROGRESS')
    )
 );
 IF score_available AND (NEW.score_release_at IS NULL OR OLD.score_release_at IS NULL OR NEW.score_release_at>OLD.score_release_at) THEN
    RAISE EXCEPTION 'Cannot revoke or postpone an already available score' USING ERRCODE='23514';
 END IF;
 IF answer_available AND (
    NEW.answer_release_at IS NULL OR OLD.answer_release_at IS NULL OR NEW.answer_release_at>OLD.answer_release_at
    OR (OLD.answer_release_mode='AFTER_VALID_SUBMISSION' AND NEW.answer_release_mode='AFTER_ASSIGNMENT_CLOSE'
         AND clock_timestamp()<OLD.closes_at)
 ) THEN
    RAISE EXCEPTION 'Cannot revoke or postpone already available answers' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_release_non_retraction BEFORE UPDATE OF score_release_at,answer_release_at,answer_release_mode
ON assignments FOR EACH ROW EXECUTE FUNCTION quizai_guard_release_non_retraction();

-- A scheduled observer may call quizai_record_current_publications under the same
-- assignment lock to record availability promptly when only server time changes.
-- Late review/result-history writes, device transfer, autosave and canonical timeout
-- behavior still require secure transactional services and integration tests.

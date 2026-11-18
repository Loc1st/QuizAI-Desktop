-- Last-step integrity guards. This migration creates NO new tables.
-- Prevent changing answer payload after it has already been scored.
CREATE FUNCTION quizai_candidate_answer_not_scored() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 -- Serialize concurrent answer insert vs score creation on the same candidate.
 PERFORM 1 FROM submission_candidates WHERE id=NEW.candidate_id FOR UPDATE;
 IF EXISTS(SELECT 1 FROM submission_scores WHERE candidate_id=NEW.candidate_id) THEN
    RAISE EXCEPTION 'Scored submission answer set is immutable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_candidate_answers_no_late_inserts BEFORE INSERT ON submission_candidate_answers
FOR EACH ROW EXECUTE FUNCTION quizai_candidate_answer_not_scored();

-- The persisted score must reflect immutable assignment snapshot + immutable candidate answers.
CREATE FUNCTION quizai_verify_persisted_score() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE expected RECORD;
BEGIN
 -- Same candidate row lock order as the answer INSERT guard.
 PERFORM 1 FROM submission_candidates WHERE id=NEW.candidate_id FOR UPDATE;
 SELECT * INTO expected FROM quizai_calculate_candidate_score(NEW.candidate_id);
 IF NOT FOUND THEN
    RAISE EXCEPTION 'Candidate has no scorable assignment snapshot' USING ERRCODE='23514';
 END IF;
 IF expected.earned_points IS DISTINCT FROM NEW.earned_points
    OR expected.possible_points IS DISTINCT FROM NEW.possible_points
    OR expected.normalized_score IS DISTINCT FROM NEW.normalized_score THEN
    RAISE EXCEPTION 'Candidate score must equal snapshot-derived score' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_score_truth BEFORE INSERT ON submission_scores
FOR EACH ROW EXECUTE FUNCTION quizai_verify_persisted_score();

-- The effective source must exist, be scored, and (when late) already be ACCEPTed.
CREATE FUNCTION quizai_check_effective_candidate_eligibility() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE candidate_kind TEXT; candidate_attempt UUID;
BEGIN
 IF NEW.effective_candidate_id IS NULL THEN
    IF TG_OP='UPDATE' AND OLD.effective_candidate_id IS NOT NULL THEN
       RAISE EXCEPTION 'Effective result cannot be cleared' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
 END IF;
 SELECT kind,attempt_id INTO candidate_kind,candidate_attempt FROM submission_candidates
 WHERE id=NEW.effective_candidate_id;
 IF NOT FOUND OR candidate_attempt<>NEW.id THEN
    RAISE EXCEPTION 'Effective candidate must belong to its own attempt'
      USING ERRCODE='23503',CONSTRAINT='fk_attempt_effective_candidate_same_attempt';
 END IF;
 IF NEW.phase<>'CLOSED' THEN
    RAISE EXCEPTION 'Cannot assign effective score to an active attempt' USING ERRCODE='23514';
 END IF;
 IF NOT EXISTS(
    SELECT 1 FROM submission_scores WHERE candidate_id=NEW.effective_candidate_id
 ) THEN
    RAISE EXCEPTION 'Effective candidate must have a computed score' USING ERRCODE='23514';
 END IF;
 IF candidate_kind='CLIENT_LATE' AND NOT EXISTS (
    SELECT 1 FROM late_submission_reviews
    WHERE candidate_id=NEW.effective_candidate_id AND decision='ACCEPT'
 ) THEN
    RAISE EXCEPTION 'Late candidate cannot become effective until accepted' USING ERRCODE='23514';
 END IF;
 IF candidate_kind='CLIENT_LATE' AND EXISTS (
    SELECT 1 FROM submission_candidates WHERE attempt_id=NEW.id AND kind='CLIENT_ON_TIME'
 ) THEN
    RAISE EXCEPTION 'Final client submission cannot be replaced by a late payload' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_effective_candidate_eligibility BEFORE INSERT OR UPDATE OF effective_candidate_id,phase ON attempts
FOR EACH ROW EXECUTE FUNCTION quizai_check_effective_candidate_eligibility();

-- Only the assignment's teacher may be recorded as reviewer; API must still authenticate that teacher.
CREATE FUNCTION quizai_check_late_review_teacher() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS (
    SELECT 1 FROM submission_candidates c JOIN attempts t ON t.id=c.attempt_id
    JOIN assignment_recipients r ON r.id=t.recipient_id
    JOIN assignments a ON a.id=r.assignment_id
    WHERE c.id=NEW.candidate_id AND c.kind='CLIENT_LATE' AND a.teacher_id=NEW.teacher_id
 ) THEN
    RAISE EXCEPTION 'Late reviewer must own assignment' USING ERRCODE='23514';
 END IF;
 IF NOT EXISTS (SELECT 1 FROM submission_scores WHERE candidate_id=NEW.candidate_id) THEN
    RAISE EXCEPTION 'Late candidate needs computed score before review' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_late_review_owner BEFORE INSERT ON late_submission_reviews
FOR EACH ROW EXECUTE FUNCTION quizai_check_late_review_teacher();

-- Preserve V2's version approval/owner guard. Serialize option changes against
-- version approval and quiz/practice use, whose guards lock the same parent row.
CREATE OR REPLACE FUNCTION quizai_guard_question_option() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v_id UUID; used BOOLEAN;
BEGIN
 v_id:=CASE WHEN TG_OP='DELETE' THEN OLD.question_version_id ELSE NEW.question_version_id END;
 IF TG_OP='UPDATE' AND OLD.question_version_id<>NEW.question_version_id THEN
    RAISE EXCEPTION 'Option cannot switch version' USING ERRCODE='23514';
 END IF;
 PERFORM 1 FROM question_versions WHERE id=v_id FOR UPDATE;
 used:=EXISTS(SELECT 1 FROM question_versions WHERE id=v_id AND approval_status='APPROVED')
       OR EXISTS(SELECT 1 FROM quiz_questions WHERE question_version_id=v_id)
       OR EXISTS(SELECT 1 FROM practice_session_questions WHERE question_version_id=v_id);
 IF used THEN
    RAISE EXCEPTION 'Approved or used question options are immutable' USING ERRCODE='23514';
 END IF;
 RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END $$;

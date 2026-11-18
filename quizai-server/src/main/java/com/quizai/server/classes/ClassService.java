package com.quizai.server.classes;

import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Shared membership/owner rules; each profile supplies its own schema mapping. */
@Service
@Profile({"dev", "test", "v2-dev", "v2-test"})
public class ClassService {
    private final ClassRepository repository;
    private final JoinCodeCodec codes;
    private final Clock clock;

    public ClassService(ClassRepository repository, JoinCodeCodec codes, Clock clock) {
        this.repository = repository;
        this.codes = codes;
        this.clock = clock;
    }

    @Transactional
    public ClassView create(SessionPrincipal principal, String name) {
        requireRole(principal, "TEACHER");
        String normalized = name(name);
        String code = newCode();
        Classroom classroom = new Classroom(UUID.randomUUID(), principal.userId(), normalized,
                codes.digest(code), true, "ACTIVE", now());
        repository.insertClass(classroom);
        return view(classroom, code);
    }

    @Transactional(readOnly = true)
    public List<ClassView> listOwned(SessionPrincipal principal) {
        requireRole(principal, "TEACHER");
        return repository.findOwnedClasses(principal.userId()).stream().map(c -> view(c, null)).toList();
    }

    @Transactional(readOnly = true)
    public ClassView get(SessionPrincipal principal, UUID classId) {
        requireAuthenticated(principal);
        Classroom classroom = repository.findClass(classId).orElseThrow(ClassService::notFound);
        if ("TEACHER".equals(principal.role())) {
            requireOwner(principal, classroom);
        } else if ("STUDENT".equals(principal.role())) {
            repository.findActiveMembership(classId, principal.userId()).orElseThrow(ClassService::notFound);
        } else {
            throw new ApiException(403, "Access denied");
        }
        return view(classroom, null);
    }

    @Transactional
    public ClassView update(SessionPrincipal principal, UUID classId, String name, String status) {
        Classroom classroom = ownedForUpdate(principal, classId);
        if (name == null && status == null) throw new ApiException(400, "At least one change is required");
        String nextName = name == null ? classroom.name() : name(name);
        String nextStatus = status == null ? classroom.status() : status;
        if (!"ACTIVE".equals(nextStatus) && !"ARCHIVED".equals(nextStatus)) {
            throw new ApiException(400, "Invalid class status");
        }
        repository.updateDetails(classId, nextName, nextStatus);
        return view(new Classroom(classId, classroom.teacherId(), nextName, classroom.joinCodeDigest(),
                classroom.joinEnabled(), nextStatus, classroom.createdAt()), null);
    }

    @Transactional
    public ClassView rotateJoinCode(SessionPrincipal principal, UUID classId) {
        Classroom classroom = ownedForUpdate(principal, classId);
        String code = newCode();
        repository.updateJoinCode(classId, codes.digest(code), classroom.joinEnabled());
        return view(classroom, code);
    }

    @Transactional
    public ClassView setJoinEnabled(SessionPrincipal principal, UUID classId, boolean enabled) {
        Classroom classroom = ownedForUpdate(principal, classId);
        String code = enabled ? newCode() : null;
        repository.updateJoinCode(classId, enabled ? codes.digest(code) : classroom.joinCodeDigest(), enabled);
        return view(new Classroom(classId, classroom.teacherId(), classroom.name(), classroom.joinCodeDigest(),
                enabled, classroom.status(), classroom.createdAt()), code);
    }

    @Transactional(readOnly = true)
    public List<ClassMemberView> members(SessionPrincipal principal, UUID classId) {
        requireRole(principal, "TEACHER");
        Classroom classroom = repository.findClass(classId).orElseThrow(ClassService::notFound);
        requireOwner(principal, classroom);
        return repository.findActiveMembers(classId);
    }

    @Transactional
    public JoinedClassView join(SessionPrincipal principal, String code) {
        requireRole(principal, "STUDENT");
        Classroom classroom = repository.findJoinableClassForUpdate(codes.digest(code))
                .orElseThrow(ClassService::notFound);
        MembershipEpisode membership = repository.findActiveMembershipForUpdate(classroom.id(), principal.userId())
                .orElseGet(() -> {
                    Instant joined = repository.latestMembershipEnd(classroom.id(), principal.userId())
                            .map(previousEnd -> after(now(), previousEnd)).orElseGet(this::now);
                    MembershipEpisode episode = new MembershipEpisode(UUID.randomUUID(), classroom.id(),
                            principal.userId(), joined, null, joined);
                    repository.insertMembership(episode);
                    return episode;
                });
        return new JoinedClassView(classroom.id(), classroom.teacherId(), classroom.name(), classroom.status(),
                classroom.joinEnabled(), membership.id(), membership.joinedAt());
    }

    @Transactional
    public void leave(SessionPrincipal principal, UUID classId) {
        requireRole(principal, "STUDENT");
        repository.findClassForUpdate(classId).orElseThrow(ClassService::notFound);
        var active = repository.findActiveMembershipForUpdate(classId, principal.userId());
        if (active.isPresent()) {
            MembershipEpisode episode = active.get();
            repository.closeMembership(episode.id(), after(now(), episode.joinedAt()));
        } else if (!repository.hasMembershipHistory(classId, principal.userId())) {
            throw notFound();
        }
    }

    private String newCode() {
        // Fail before acquiring a database lock when no HMAC key is configured.
        String code = codes.generateCode();
        repository.lockJoinCodeGeneration();
        for (int attempt = 0; attempt < 8; attempt++) {
            if (!repository.enabledDigestExists(codes.digest(code))) return code;
            if (attempt < 7) code = codes.generateCode();
        }
        throw new ApiException(503, "Join codes are temporarily unavailable");
    }

    private Classroom ownedForUpdate(SessionPrincipal principal, UUID classId) {
        requireRole(principal, "TEACHER");
        Classroom classroom = repository.findClassForUpdate(classId).orElseThrow(ClassService::notFound);
        requireOwner(principal, classroom);
        return classroom;
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }

    private static Instant after(Instant current, Instant previous) {
        return current.isAfter(previous) ? current : previous.plus(1, ChronoUnit.MICROS);
    }

    private static String name(String value) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > 200) {
            throw new ApiException(400, "Class name must contain 1 to 200 characters");
        }
        return value.trim();
    }

    private static ClassView view(Classroom classroom, String code) {
        return new ClassView(classroom.id(), classroom.teacherId(), classroom.name(), classroom.status(),
                classroom.joinEnabled(), code);
    }

    private static void requireOwner(SessionPrincipal principal, Classroom classroom) {
        if (!principal.userId().equals(classroom.teacherId())) throw notFound();
    }

    private static void requireAuthenticated(SessionPrincipal principal) {
        if (principal == null) throw new ApiException(401, "Authentication required");
    }

    private static void requireRole(SessionPrincipal principal, String role) {
        requireAuthenticated(principal);
        if (!role.equals(principal.role())) throw new ApiException(403, "Access denied");
    }

    private static ApiException notFound() { return new ApiException(404, "Class not found"); }
}

package com.quizai.server.classes;

import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClassServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-08T08:00:00.123456Z");
    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private final ClassRepository repository = mock(ClassRepository.class);
    private final JoinCodeCodec codes = new JoinCodeCodec(KEY);
    private final UUID classId = UUID.randomUUID();
    private final SessionPrincipal teacher = principal("TEACHER");
    private final SessionPrincipal student = principal("STUDENT");
    private ClassService service;

    @BeforeEach
    void setUp() {
        service = new ClassService(repository, codes, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void teacherCreationStoresOnlyDigestAndDisclosesPlaintextOnce() {
        ClassView created = service.create(teacher, "  Algorithms  ");

        ArgumentCaptor<Classroom> stored = ArgumentCaptor.forClass(Classroom.class);
        verify(repository).insertClass(stored.capture());
        assertEquals(teacher.userId(), stored.getValue().teacherId());
        assertEquals("Algorithms", created.name());
        assertEquals("ACTIVE", created.status());
        assertTrue(created.joinEnabled());
        assertNotNull(created.joinCode());
        assertEquals(codes.digest(created.joinCode()), stored.getValue().joinCodeDigest());
        assertNotEquals(created.joinCode(), stored.getValue().joinCodeDigest());

        when(repository.findOwnedClasses(teacher.userId())).thenReturn(List.of(stored.getValue()));
        assertNull(service.listOwned(teacher).getFirst().joinCode());
    }

    @Test
    void studentsCannotCreateClassesEvenWhenServiceIsCalledDirectly() {
        assertStatus(403, () -> service.create(student, "Class"));
        verifyNoInteractions(repository);
    }

    @Test
    void anotherTeacherCannotEditRotateOrReadMembers() {
        when(repository.findClassForUpdate(classId)).thenReturn(Optional.of(classroom("ACTIVE", true)));
        when(repository.findClass(classId)).thenReturn(Optional.of(classroom("ACTIVE", true)));
        SessionPrincipal stranger = principal("TEACHER");

        assertStatus(404, () -> service.update(stranger, classId, "Changed", null));
        assertStatus(404, () -> service.rotateJoinCode(stranger, classId));
        assertStatus(404, () -> service.setJoinEnabled(stranger, classId, false));
        assertStatus(404, () -> service.members(stranger, classId));
        verify(repository, never()).updateDetails(any(), any(), any());
        verify(repository, never()).updateJoinCode(any(), any(), anyBoolean());
        verify(repository, never()).lockJoinCodeGeneration();
    }

    @Test
    void rotationPreservesDisabledStateAndReturnsFreshCode() {
        Classroom disabled = classroom("ACTIVE", false);
        when(repository.findClassForUpdate(classId)).thenReturn(Optional.of(disabled));

        ClassView rotated = service.rotateJoinCode(teacher, classId);

        assertFalse(rotated.joinEnabled());
        assertNotNull(rotated.joinCode());
        verify(repository).updateJoinCode(classId, codes.digest(rotated.joinCode()), false);
        InOrder locks = inOrder(repository);
        locks.verify(repository).findClassForUpdate(classId);
        locks.verify(repository).lockJoinCodeGeneration();
    }

    @Test
    void enablingRotatesTheCodeWhileDisablingDoesNotDiscloseIt() {
        when(repository.findClassForUpdate(classId)).thenReturn(Optional.of(classroom("ACTIVE", false)));
        ClassView enabled = service.setJoinEnabled(teacher, classId, true);
        assertTrue(enabled.joinEnabled());
        verify(repository).updateJoinCode(classId, codes.digest(enabled.joinCode()), true);

        ClassView disabled = service.setJoinEnabled(teacher, classId, false);
        assertFalse(disabled.joinEnabled());
        assertNull(disabled.joinCode());
    }

    @Test
    void invalidAndDisabledOrArchivedCodesCannotCreateMemberships() {
        String code = codes.generateCode();
        String digest = codes.digest(code);
        when(repository.findJoinableClassForUpdate(digest)).thenReturn(Optional.empty());

        assertStatus(404, () -> service.join(student, code));
        verify(repository, never()).insertMembership(any());
        assertStatus(403, () -> service.join(teacher, code));
    }

    @Test
    void joiningExistingActiveEpisodeIsIdempotentAfterTheClassLock() {
        String code = codes.generateCode();
        MembershipEpisode original = membership(UUID.randomUUID(), NOW.minusSeconds(60), null);
        when(repository.findJoinableClassForUpdate(codes.digest(code)))
                .thenReturn(Optional.of(classroom("ACTIVE", true)));
        when(repository.findActiveMembershipForUpdate(classId, student.userId())).thenReturn(Optional.of(original));

        JoinedClassView first = service.join(student, code);
        JoinedClassView replay = service.join(student, code.toLowerCase(java.util.Locale.ROOT));

        assertEquals(original.id(), first.membershipId());
        assertEquals(first, replay);
        verify(repository, never()).insertMembership(any());
        InOrder locks = inOrder(repository);
        locks.verify(repository).findJoinableClassForUpdate(anyString());
        locks.verify(repository).findActiveMembershipForUpdate(classId, student.userId());
    }

    @Test
    void rejoiningAppendsANewEpisodeAfterThePreviousClosedInterval() {
        String code = codes.generateCode();
        UUID oldMembership = UUID.randomUUID();
        Instant previousEnd = NOW.plusNanos(1_000);
        when(repository.findJoinableClassForUpdate(codes.digest(code)))
                .thenReturn(Optional.of(classroom("ACTIVE", true)));
        when(repository.findActiveMembershipForUpdate(classId, student.userId())).thenReturn(Optional.empty());
        when(repository.latestMembershipEnd(classId, student.userId())).thenReturn(Optional.of(previousEnd));

        JoinedClassView joined = service.join(student, code);

        ArgumentCaptor<MembershipEpisode> inserted = ArgumentCaptor.forClass(MembershipEpisode.class);
        verify(repository).insertMembership(inserted.capture());
        assertNotEquals(oldMembership, joined.membershipId());
        assertEquals(inserted.getValue().id(), joined.membershipId());
        assertTrue(joined.joinedAt().isAfter(previousEnd));
        assertNull(inserted.getValue().leftAt());
        verify(repository, never()).closeMembership(any(), any());
    }

    @Test
    void leavingOnlyClosesTheActiveEpisodeAtPostgresqlMicrosecondPrecision() {
        MembershipEpisode original = membership(UUID.randomUUID(), NOW, null);
        when(repository.findClassForUpdate(classId)).thenReturn(Optional.of(classroom("ACTIVE", true)));
        when(repository.findActiveMembershipForUpdate(classId, student.userId())).thenReturn(Optional.of(original));
        when(repository.closeMembership(any(), any())).thenReturn(1);

        service.leave(student, classId);

        verify(repository).closeMembership(original.id(), NOW.plusNanos(1_000));
        verify(repository, never()).insertMembership(any());
        InOrder locks = inOrder(repository);
        locks.verify(repository).findClassForUpdate(classId);
        locks.verify(repository).findActiveMembershipForUpdate(classId, student.userId());
        locks.verify(repository).closeMembership(any(), any());
    }

    @Test
    void repeatedLeaveDoesNotModifyClosedEpisodesAndOutsidersGet404() {
        when(repository.findClassForUpdate(classId)).thenReturn(Optional.of(classroom("ACTIVE", true)));
        when(repository.findActiveMembershipForUpdate(classId, student.userId())).thenReturn(Optional.empty());
        when(repository.hasMembershipHistory(classId, student.userId())).thenReturn(true);
        service.leave(student, classId);
        verify(repository, never()).closeMembership(any(), any());

        when(repository.hasMembershipHistory(classId, student.userId())).thenReturn(false);
        assertStatus(404, () -> service.leave(student, classId));
    }

    @Test
    void studentClassReadRequiresAnActiveMembership() {
        when(repository.findClass(classId)).thenReturn(Optional.of(classroom("ACTIVE", true)));
        when(repository.findActiveMembership(classId, student.userId())).thenReturn(Optional.empty());
        assertStatus(404, () -> service.get(student, classId));

        when(repository.findActiveMembership(classId, student.userId()))
                .thenReturn(Optional.of(membership(UUID.randomUUID(), NOW, null)));
        assertNull(service.get(student, classId).joinCode());
    }

    @Test
    void unavailableKeyLeavesClassReadsAndDisableAvailable() {
        ClassService unavailable = new ClassService(repository, new JoinCodeCodec(""), Clock.systemUTC());
        when(repository.findClassForUpdate(classId)).thenReturn(Optional.of(classroom("ACTIVE", true)));
        when(repository.findClass(classId)).thenReturn(Optional.of(classroom("ACTIVE", true)));

        assertNotNull(unavailable.get(teacher, classId));
        assertFalse(unavailable.setJoinEnabled(teacher, classId, false).joinEnabled());
        assertStatus(503, () -> unavailable.rotateJoinCode(teacher, classId));
        assertStatus(503, () -> unavailable.create(teacher, "Class"));
    }

    @Test
    void codeCollisionRetriesAreBoundedWithoutCatchingTransactionErrors() {
        when(repository.enabledDigestExists(anyString())).thenReturn(true);
        assertStatus(503, () -> service.create(teacher, "Class"));
        verify(repository, times(8)).enabledDigestExists(anyString());
        verify(repository, never()).insertClass(any());
    }

    @Test
    void namesAreValidatedAndPatchRequiresAtLeastOneChange() {
        assertStatus(400, () -> service.create(teacher, "  "));
        assertStatus(400, () -> service.create(teacher, "X".repeat(201)));
        when(repository.findClassForUpdate(classId)).thenReturn(Optional.of(classroom("ACTIVE", true)));
        assertStatus(400, () -> service.update(teacher, classId, null, null));
        assertStatus(400, () -> service.update(teacher, classId, " ", null));
    }

    private Classroom classroom(String status, boolean enabled) {
        return new Classroom(classId, teacher.userId(), "Class", "old-digest", enabled, status, NOW.minusSeconds(60));
    }

    private MembershipEpisode membership(UUID id, Instant joined, Instant left) {
        return new MembershipEpisode(id, classId, student.userId(), joined, left, joined);
    }

    private static SessionPrincipal principal(String role) {
        return new SessionPrincipal(UUID.randomUUID(), role.toLowerCase(java.util.Locale.ROOT) + "@example.invalid",
                role, UUID.randomUUID(), NOW.plusSeconds(3_600));
    }

    private static void assertStatus(int expected, org.junit.jupiter.api.function.Executable operation) {
        assertEquals(expected, assertThrows(ApiException.class, operation).status());
    }
}

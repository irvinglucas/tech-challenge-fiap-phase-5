package com.fiap.prontuario.command.domain;

import com.fiap.prontuario.common.event.AccessGranted;
import com.fiap.prontuario.common.event.AccessRevoked;
import com.fiap.prontuario.common.event.AllergyRegistered;
import com.fiap.prontuario.common.event.ConsultationRecorded;
import com.fiap.prontuario.common.event.DiagnosisAdded;
import com.fiap.prontuario.common.event.ExamResultAttached;
import com.fiap.prontuario.common.event.PatientRecordEvent;
import com.fiap.prontuario.common.event.PatientRegistered;
import com.fiap.prontuario.common.event.PrescriptionIssued;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Orquestra os comandos do dominio de prontuario (ver docs/event-storming.md):
 * valida as precondicoes contra o estado atual do agregado {@link
 * PatientRecord} e grava o evento resultante via {@link PatientRecordRepository},
 * que aplica o controle de concorrencia otimista (issue #4).
 */
@ApplicationScoped
public class PatientCommandService {

    private final PatientRecordRepository repository;

    @Inject
    public PatientCommandService(PatientRecordRepository repository) {
        this.repository = repository;
    }

    /** @return o patientId (CPF normalizado) e a nova versao do agregado. */
    public CommandResult registerPatient(String fullName, String cpf, String unitId) {
        String patientId = normalizeCpf(cpf);
        PatientRecord record = repository.load(patientId);
        if (record.isRegistered()) {
            throw new PatientAlreadyRegisteredException(patientId);
        }

        PatientRegistered event = new PatientRegistered(
                UUID.randomUUID(), patientId, Instant.now(), fullName, patientId, unitId);
        int newVersion = repository.append(patientId, record.version(), event);
        return new CommandResult(patientId, newVersion);
    }

    public CommandResult recordConsultation(String patientId, String professionalId, String unitId, String notes) {
        return applyClinicalCommand(patientId, professionalId, () -> new ConsultationRecorded(
                UUID.randomUUID(), patientId, Instant.now(), professionalId, unitId, notes));
    }

    public CommandResult addDiagnosis(String patientId, String professionalId, String unitId,
            String description, String cid10) {
        return applyClinicalCommand(patientId, professionalId, () -> new DiagnosisAdded(
                UUID.randomUUID(), patientId, Instant.now(), professionalId, unitId, description, cid10));
    }

    public CommandResult issuePrescription(String patientId, String professionalId, String unitId,
            String medication, String dosage) {
        return applyClinicalCommand(patientId, professionalId, () -> new PrescriptionIssued(
                UUID.randomUUID(), patientId, Instant.now(), professionalId, unitId, medication, dosage));
    }

    public CommandResult registerAllergy(String patientId, String professionalId, String unitId,
            String substance, String severity) {
        return applyClinicalCommand(patientId, professionalId, () -> new AllergyRegistered(
                UUID.randomUUID(), patientId, Instant.now(), professionalId, unitId, substance, severity));
    }

    public CommandResult attachExamResult(String patientId, String professionalId, String unitId,
            String examType, String resultSummary) {
        return applyClinicalCommand(patientId, professionalId, () -> new ExamResultAttached(
                UUID.randomUUID(), patientId, Instant.now(), professionalId, unitId, examType, resultSummary));
    }

    public CommandResult grantAccess(String patientId, String grantedBy, String professionalId, String unitId) {
        PatientRecord record = requireRegistered(patientId);
        AccessGranted event = new AccessGranted(
                UUID.randomUUID(), patientId, Instant.now(), grantedBy, professionalId, unitId);
        int newVersion = repository.append(patientId, record.version(), event);
        return new CommandResult(patientId, newVersion);
    }

    public CommandResult revokeAccess(String patientId, String revokedBy, String professionalId) {
        PatientRecord record = requireRegistered(patientId);
        if (!record.isAuthorized(professionalId)) {
            throw new AccessNotGrantedException(patientId, professionalId);
        }
        AccessRevoked event = new AccessRevoked(
                UUID.randomUUID(), patientId, Instant.now(), revokedBy, professionalId);
        int newVersion = repository.append(patientId, record.version(), event);
        return new CommandResult(patientId, newVersion);
    }

    private CommandResult applyClinicalCommand(
            String patientId, String professionalId, Supplier<PatientRecordEvent> eventFactory) {
        PatientRecord record = requireRegistered(patientId);
        if (!record.isAuthorized(professionalId)) {
            throw new UnauthorizedAccessException(patientId, professionalId);
        }
        PatientRecordEvent event = eventFactory.get();
        int newVersion = repository.append(patientId, record.version(), event);
        return new CommandResult(patientId, newVersion);
    }

    private PatientRecord requireRegistered(String patientId) {
        PatientRecord record = repository.load(patientId);
        if (!record.isRegistered()) {
            throw new PatientNotRegisteredException(patientId);
        }
        return record;
    }

    private String normalizeCpf(String cpf) {
        return cpf.replaceAll("\\D", "");
    }

    public record CommandResult(String patientId, int version) {
    }
}

package com.fiap.prontuario.audit.log;

import com.fiap.prontuario.common.event.AccessDenied;
import com.fiap.prontuario.common.event.AccessGranted;
import com.fiap.prontuario.common.event.AccessRevoked;
import com.fiap.prontuario.common.event.AllergyRegistered;
import com.fiap.prontuario.common.event.ConsultationRecorded;
import com.fiap.prontuario.common.event.DiagnosisAdded;
import com.fiap.prontuario.common.event.ExamResultAttached;
import com.fiap.prontuario.common.event.PatientRecordEvent;
import com.fiap.prontuario.common.event.PatientRegistered;
import com.fiap.prontuario.common.event.PrescriptionIssued;
import com.fiap.prontuario.common.event.RecordAccessed;

/**
 * Extrai de cada variante de {@link PatientRecordEvent} os campos comuns da
 * trilha de auditoria: quem (professionalId), de onde (unitId) e um detalhe
 * textual do que aconteceu. Cada evento carrega esses dados em campos com
 * nomes diferentes (ver docs/event-storming.md), entao a extracao e feita
 * evento a evento.
 */
public final class AuditFieldsExtractor {

    private AuditFieldsExtractor() {
    }

    public static AuditFields extract(PatientRecordEvent event) {
        return switch (event) {
            case PatientRegistered e -> new AuditFields(null, e.unitId(), "Paciente registrado: " + e.fullName());
            case ConsultationRecorded e -> new AuditFields(e.professionalId(), e.unitId(), "Consulta registrada");
            case DiagnosisAdded e -> new AuditFields(e.professionalId(), e.unitId(),
                    "Diagnostico adicionado: " + e.cid10());
            case PrescriptionIssued e -> new AuditFields(e.professionalId(), e.unitId(),
                    "Prescricao emitida: " + e.medication());
            case AllergyRegistered e -> new AuditFields(e.professionalId(), e.unitId(),
                    "Alergia registrada: " + e.substance());
            case ExamResultAttached e -> new AuditFields(e.professionalId(), e.unitId(),
                    "Resultado de exame anexado: " + e.examType());
            case AccessGranted e -> new AuditFields(e.professionalId(), e.unitId(),
                    "Acesso concedido por " + e.grantedBy());
            case AccessRevoked e -> new AuditFields(e.professionalId(), null,
                    "Acesso revogado por " + e.revokedBy());
            case RecordAccessed e -> new AuditFields(e.professionalId(), e.unitId(), "Prontuario consultado");
            case AccessDenied e -> new AuditFields(e.professionalId(), e.unitId(),
                    "Acesso negado: " + e.reason());
        };
    }

    public record AuditFields(String professionalId, String unitId, String detail) {
    }
}

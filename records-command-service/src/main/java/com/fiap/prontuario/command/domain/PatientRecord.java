package com.fiap.prontuario.command.domain;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Agregado PatientRecord (ver docs/event-storming.md). Nao e persistido
 * diretamente: seu estado e sempre reconstruido a partir do replay dos
 * eventos gravados no event store ({@link #replay(String, List)}).
 *
 * <p>A versao do agregado (numero de eventos aplicados) e a base do
 * controle de concorrencia otimista: quem for gravar um novo evento precisa
 * informar a versao que leu, e o event store rejeita a gravacao se algum
 * outro comando já tiver avancado essa versao entre a leitura e a escrita.
 */
public final class PatientRecord {

    private final String patientId;
    private int version;
    private boolean registered;
    private String fullName;
    private String cpf;
    private String unitId;

    private final List<String> diagnoses = new ArrayList<>();
    private final List<String> prescriptions = new ArrayList<>();
    private final List<String> allergies = new ArrayList<>();
    private final List<String> examResults = new ArrayList<>();
    private final Set<String> authorizedProfessionalIds = new LinkedHashSet<>();

    private PatientRecord(String patientId) {
        this.patientId = patientId;
    }

    /**
     * Reconstroi o estado do agregado aplicando, em ordem, os eventos
     * carregados do event store. Uma lista vazia representa um paciente
     * ainda nao registrado (version 0).
     */
    public static PatientRecord replay(String patientId, List<PatientRecordEvent> events) {
        PatientRecord record = new PatientRecord(patientId);
        for (PatientRecordEvent event : events) {
            record.apply(event);
        }
        return record;
    }

    private void apply(PatientRecordEvent event) {
        switch (event) {
            case PatientRegistered e -> {
                this.registered = true;
                this.fullName = e.fullName();
                this.cpf = e.cpf();
                this.unitId = e.unitId();
            }
            case ConsultationRecorded e -> {
                // Consultas nao alteram nenhum estado consultavel pelo agregado
                // de escrita hoje; o historico completo fica no event store e
                // e materializado pelo records-query-service (issue #8).
            }
            case DiagnosisAdded e -> diagnoses.add(e.description());
            case PrescriptionIssued e -> prescriptions.add(e.medication());
            case AllergyRegistered e -> allergies.add(e.substance());
            case ExamResultAttached e -> examResults.add(e.examType());
            case AccessGranted e -> authorizedProfessionalIds.add(e.professionalId());
            case AccessRevoked e -> authorizedProfessionalIds.remove(e.professionalId());
            case RecordAccessed e -> {
                // Efeito colateral de leitura, nao altera o estado do agregado.
            }
            case AccessDenied e -> {
                // Idem: tentativa de acesso negada nao altera o estado do agregado.
            }
        }
        this.version++;
    }

    public boolean isRegistered() {
        return registered;
    }

    public boolean isAuthorized(String professionalId) {
        return authorizedProfessionalIds.contains(professionalId);
    }

    public String patientId() {
        return patientId;
    }

    public int version() {
        return version;
    }

    public String fullName() {
        return fullName;
    }

    public String cpf() {
        return cpf;
    }

    public String unitId() {
        return unitId;
    }

    public List<String> diagnoses() {
        return Collections.unmodifiableList(diagnoses);
    }

    public List<String> prescriptions() {
        return Collections.unmodifiableList(prescriptions);
    }

    public List<String> allergies() {
        return Collections.unmodifiableList(allergies);
    }

    public List<String> examResults() {
        return Collections.unmodifiableList(examResults);
    }

    public Set<String> authorizedProfessionalIds() {
        return Collections.unmodifiableSet(authorizedProfessionalIds);
    }
}

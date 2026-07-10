package com.fiap.prontuario.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Serializa/desserializa eventos de {@link PatientRecordEvent} para o formato
 * usado tanto na coluna {@code payload} (JSONB) do event store quanto nas
 * mensagens publicadas no broker de eventos.
 *
 * <p>O nome simples da classe do evento (ex.: {@code "PatientRegistered"}) e
 * usado como discriminador de tipo, guardado separadamente (coluna
 * {@code event_type} / header da mensagem) para permitir a desserializacao
 * de volta para o record especifico.
 */
@ApplicationScoped
public class PatientRecordEventCodec {

    private final ObjectMapper objectMapper;

    @Inject
    public PatientRecordEventCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String typeOf(PatientRecordEvent event) {
        return event.getClass().getSimpleName();
    }

    public String toJson(PatientRecordEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException("Falha ao serializar evento " + typeOf(event), e);
        }
    }

    public PatientRecordEvent fromJson(String eventType, String json) {
        Class<? extends PatientRecordEvent> eventClass = classFor(eventType);
        try {
            return objectMapper.readValue(json, eventClass);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException("Falha ao desserializar evento " + eventType, e);
        }
    }

    private Class<? extends PatientRecordEvent> classFor(String eventType) {
        return switch (eventType) {
            case "PatientRegistered" -> PatientRegistered.class;
            case "ConsultationRecorded" -> ConsultationRecorded.class;
            case "DiagnosisAdded" -> DiagnosisAdded.class;
            case "PrescriptionIssued" -> PrescriptionIssued.class;
            case "AllergyRegistered" -> AllergyRegistered.class;
            case "ExamResultAttached" -> ExamResultAttached.class;
            case "AccessGranted" -> AccessGranted.class;
            case "AccessRevoked" -> AccessRevoked.class;
            case "RecordAccessed" -> RecordAccessed.class;
            case "AccessDenied" -> AccessDenied.class;
            default -> throw new EventSerializationException("Tipo de evento desconhecido: " + eventType);
        };
    }
}

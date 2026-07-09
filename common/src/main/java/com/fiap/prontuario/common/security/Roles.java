package com.fiap.prontuario.common.security;

/**
 * Papeis (roles) usados no controle de acesso (RBAC) do dominio de
 * prontuario. Usado pelo JWT emitido/validado pelos servicos (ver issue
 * #7 - Autenticacao JWT + RBAC no command-service).
 */
public final class Roles {

    public static final String MEDICO = "MEDICO";
    public static final String ENFERMEIRO = "ENFERMEIRO";
    public static final String GESTOR = "GESTOR";
    public static final String AUDITOR = "AUDITOR";

    /** Papeis autorizados a registrar evolucao clinica (consulta, diagnostico, prescricao, alergia, exame). */
    public static final String[] CLINICAL_WRITERS = {MEDICO, ENFERMEIRO};

    private Roles() {
    }
}

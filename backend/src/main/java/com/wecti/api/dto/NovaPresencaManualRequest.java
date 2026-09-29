package com.wecti.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Admin afirmando que um aluno participou da palestra, no lugar da
 * leitura de QR code que nao deu certo - ver PresencaManualService.
 *
 * <p>A justificativa e obrigatoria de proposito. E a unica explicacao que
 * vai sobrar, depois, para uma presenca que nao tem leitura de QR por
 * tras; sem ela a lista final teria presencas afirmadas sem ninguem
 * lembrar do motivo.
 *
 * <p>Nao ha campo de horario: entrada e saida sao as do proprio evento
 * (ver PresencaManualService), e nao o instante do clique.
 */
public record NovaPresencaManualRequest(
        @NotNull UUID alunoId,
        @NotBlank @Size(max = 200, message = "Justificativa deve ter no maximo 200 caracteres")
        String justificativa) {
}

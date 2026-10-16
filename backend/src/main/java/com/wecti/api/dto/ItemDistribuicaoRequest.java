package com.wecti.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Uma linha da distribuicao: quantos pontos vao para aquela turma.
 *
 * <p>Nao ha campo de nota: a nota e derivada dos pontos na leitura (ver
 * RegraDistribuicao). Aceitar nota aqui deixaria o cliente decidir a
 * conversao, e duas telas poderiam discordar do mesmo numero.
 */
public record ItemDistribuicaoRequest(
        @NotNull UUID disciplinaId,
        @NotNull @Positive(message = "Os pontos de cada turma devem ser maiores que zero")
        Integer pontos) {
}

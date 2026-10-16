package com.wecti.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Estado COMPLETO da distribuicao do aluno - o que vier aqui substitui o
 * que estava gravado (ver DistribuicaoPontuacaoService.salvar).
 *
 * <p>Lista vazia e valida: e como o aluno zera tudo e recomeca.
 */
public record SalvarDistribuicaoRequest(
        @NotNull @Valid List<ItemDistribuicaoRequest> itens) {
}

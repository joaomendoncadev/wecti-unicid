package com.wecti.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Linha da consulta do admin: um aluno e como ele repartiu a pontuacao.
 *
 * <p>Agrupado por aluno, e nao uma linha solta por turma, porque a
 * pergunta que o admin faz e "o que o fulano distribuiu?" - e conferir
 * se a soma fecha exige ver as linhas dele juntas.
 */
public record DistribuicaoDoAlunoResponse(
        String alunoNome,
        String alunoRgm,
        int totalPontos,
        BigDecimal totalNota,
        List<ItemDistribuicaoResponse> itens) {
}

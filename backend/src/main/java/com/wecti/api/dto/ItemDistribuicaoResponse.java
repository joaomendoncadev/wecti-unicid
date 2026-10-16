package com.wecti.api.dto;

import com.wecti.api.domain.DistribuicaoPontuacao;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Uma linha da distribuicao, ja com a turma e a nota resolvidas - e o
 * que o comprovante do aluno imprime e o que a consulta do admin mostra.
 */
public record ItemDistribuicaoResponse(
        UUID disciplinaId,
        String codigo,
        String disciplina,
        String turno,
        String diaSemana,
        String professor,
        int pontos,
        BigDecimal nota) {

    public static ItemDistribuicaoResponse de(DistribuicaoPontuacao d, BigDecimal nota) {
        var turma = d.getDisciplina();
        return new ItemDistribuicaoResponse(turma.getId(), turma.getCodigo(), turma.getNome(),
                turma.getTurno().name(), turma.getDiaSemana(), turma.getProfessor(),
                d.getPontos(), nota);
    }
}

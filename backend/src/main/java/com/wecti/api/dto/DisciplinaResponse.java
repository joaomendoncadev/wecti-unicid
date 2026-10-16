package com.wecti.api.dto;

import com.wecti.api.domain.Disciplina;

import java.util.UUID;

/** Uma turma do catalogo (ver Disciplina). */
public record DisciplinaResponse(
        UUID id,
        String codigo,
        String nome,
        String turno,
        String diaSemana,
        String professor) {

    public static DisciplinaResponse de(Disciplina d) {
        return new DisciplinaResponse(d.getId(), d.getCodigo(), d.getNome(),
                d.getTurno().name(), d.getDiaSemana(), d.getProfessor());
    }
}

package com.wecti.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Linha da lista de contato dos inscritos num evento
 * (GET /eventos/{id}/inscritos) - nome e e-mail de quem esta na turma,
 * para a organizacao avisar mudanca de sala, mandar material ou cobrar
 * presenca.
 *
 * <p>Existe separado de {@link InscricaoResponse} de proposito. Aquele
 * descreve a <b>inscricao</b> (status, check-in, certificado) e e usado
 * tambem em /me/inscricoes, que o proprio aluno chama; colocar e-mail la
 * entregaria contato de aluno em resposta que nao precisa dele. Este
 * descreve a <b>pessoa</b>, e so o admin alcanca.
 *
 * <p>Montado direto pela consulta (projecao JPQL em
 * InscricaoRepository.findInscritosDoEvento), sem passar pelas entidades -
 * assim o hash de senha do aluno nem sai do banco.
 */
public record InscritoEventoResponse(
        UUID alunoId,
        String nome,
        String email,
        String rgm,
        LocalDateTime inscritoEm) {
}

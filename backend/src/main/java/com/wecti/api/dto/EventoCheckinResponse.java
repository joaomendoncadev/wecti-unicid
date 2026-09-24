package com.wecti.api.dto;

import com.wecti.api.domain.Checkin;
import com.wecti.api.domain.Evento;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Linha do relatório de presença de um evento (AdminCheckinPage -
 * "Participantes do Evento") - já traz nome/RGM do aluno, sem o front
 * precisar cruzar com GET /usuarios.
 */
public record EventoCheckinResponse(
        UUID inscricaoId,
        UUID alunoId,
        String alunoNome,
        String alunoRgm,
        LocalDateTime entrada,
        LocalDateTime saida,
        Float percentualPresenca) {

    public static EventoCheckinResponse de(Checkin checkin, Evento evento) {
        var aluno = checkin.getInscricao().getAluno();
        Float percentual = null;
        if (checkin.getSaida() != null) {
            // Informativo para o admin na lista de presenca: mostra
            // quanto o aluno ficou, mas nao decide pontuacao nem
            // certificado desde que a exigencia de 75% saiu (setembro
            // de 2026). Segundos, nao minutos - toMinutes() trunca a
            // fracao e distorce o percentual em eventos curtos.
            long duracaoEvento = Duration.between(evento.getDataHoraInicio(), evento.getDataHoraFim()).toSeconds();
            long permanencia = Duration.between(checkin.getEntrada(), checkin.getSaida()).toSeconds();
            percentual = duracaoEvento > 0 ? (float) (100.0 * permanencia / duracaoEvento) : 0f;
        }
        return new EventoCheckinResponse(checkin.getInscricao().getId(), aluno.getId(), aluno.getNome(),
                aluno.getRgm(), checkin.getEntrada(), checkin.getSaida(), percentual);
    }
}

package com.wecti.api.dto;

import com.wecti.api.domain.Checkin;
import com.wecti.api.domain.Evento;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

public record CheckinResponse(
        UUID id,
        UUID inscricaoId,
        LocalDateTime entrada,
        LocalDateTime saida,
        Float percentualPresenca) {

    public static CheckinResponse de(Checkin checkin, Evento evento) {
        Float percentual = null;
        if (checkin.getSaida() != null) {
            // Informativo: mostra quanto o aluno ficou, mas nao decide
            // nada desde que a exigencia de 75% saiu (setembro de 2026).
            // Segundos, nao minutos - toMinutes() trunca a fracao e
            // distorce muito o percentual em eventos curtos.
            long duracaoEvento = Duration.between(evento.getDataHoraInicio(), evento.getDataHoraFim()).toSeconds();
            long permanencia = Duration.between(checkin.getEntrada(), checkin.getSaida()).toSeconds();
            percentual = duracaoEvento > 0 ? (float) (100.0 * permanencia / duracaoEvento) : 0f;
        }
        return new CheckinResponse(checkin.getId(), checkin.getInscricao().getId(),
                checkin.getEntrada(), checkin.getSaida(), percentual);
    }
}

package com.wecti.api.dto;

import java.util.List;

/**
 * Pontuacao do aluno no WECTI.
 *
 * <p>Sem recorte por semestre: o conceito de "periodo" saiu do sistema
 * (ver V7__remove_periodo.sql). O total e simplesmente o que o aluno
 * somou.
 *
 * @param pontosTotal    o que vale no ranking: eventos + extras
 * @param pontosEventos  parcela vinda das palestras assistidas, ja com o
 *                       teto aplicado e as faltas descontadas
 * @param pontosExtras   parcela lancada pelo admin (gincanas) - fora do
 *                       teto de eventos
 * @param limiteEventos  teto de pontos que se pode ganhar em palestras
 * @param pontosEventosExcedente quanto dos ganhos o teto cortou; zero na
 *                       maioria dos casos. Vai na resposta porque sem ele
 *                       o aluno soma a lista de eventos a mao, da
 *                       diferente de pontosEventos e acha que ha um erro
 * @param extras         lancamentos com motivo e autor - sem isso o aluno
 *                       ve o total mudar e nao tem como saber por que
 */
public record PontuacaoResponse(
        int pontosTotal,
        int pontosEventos,
        int pontosExtras,
        int limiteEventos,
        int pontosEventosExcedente,
        List<EventoPontuacaoItemResponse> eventos,
        List<PontuacaoExtraResponse> extras) {
}

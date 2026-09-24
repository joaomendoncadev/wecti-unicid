package com.wecti.api.service;

import com.wecti.api.domain.Checkin;
import com.wecti.api.domain.Evento;
import com.wecti.api.domain.Inscricao;
import com.wecti.api.domain.InscricaoStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Quanto os eventos valem para um aluno. Regra unica, num lugar so.
 *
 * <p>Existe porque a mesma conta e feita em dois lugares que precisam
 * concordar: a pontuacao individual ({@link PontuacaoService}) e o
 * ranking ({@link RankingService}). Se cada um implementasse a regra, o
 * aluno veria um total na propria tela e outro no ranking - e o erro so
 * apareceria com a competicao ja em andamento.
 *
 * <p>A diferenca entre os dois nao esta na regra, e sim em como os dados
 * chegam: a tela individual busca inscricao e check-in por evento; o
 * ranking carrega tudo de uma vez e distribui. Por isso {@link #avaliar}
 * recebe os objetos prontos, sem consultar nada, e o fechamento da conta
 * fica no {@link Totalizador}.
 *
 * <p><b>As duas regras que o professor fechou em setembro de 2026:</b>
 *
 * <ul>
 *   <li><b>No-show custa um valor fixo</b> ({@code app.pontuacao.penalidade-no-show},
 *       padrao 100), e nao mais o que aquele evento valia. Antes, faltar
 *       numa palestra de 50 pontos custava 50 e faltar numa de 300 custava
 *       300 - quem reservava vaga nas palestras mais concorridas e nao
 *       aparecia era, na pratica, quem mais perdia. Com valor fixo, a
 *       vaga desperdicada custa o mesmo em qualquer palestra.</li>
 *   <li><b>Teto para o que se ganha em eventos</b>
 *       ({@code app.pontuacao.limite-eventos}, padrao 2000). O teto trava
 *       apenas os ganhos; a penalidade de no-show e descontada depois
 *       dele. Sem essa ordem, quem ja estivesse acima do teto poderia
 *       faltar de graca - a falta sumiria na hora de aparar o excedente.
 *       Gincana ({@link PontuacaoExtraService}) fica fora do teto: e
 *       premiacao lancada a mao pelo admin, nao pontuacao de palestra.</li>
 * </ul>
 */
@Service
public class RegraPontuacao {

    /** Aluno cumpriu o criterio e levou os pontos do evento. */
    static final String CONCLUIDO = "concluido";
    /** Nao cancelou e nao compareceu - leva a penalidade fixa. */
    static final String NO_SHOW = "no_show";
    /** Cancelou no prazo - nao pontua nem penaliza. */
    static final String CANCELADO = "cancelado";

    private final int penalidadeNoShow;
    private final int limiteEventos;

    public RegraPontuacao(
            @Value("${app.pontuacao.penalidade-no-show}") int penalidadeNoShow,
            @Value("${app.pontuacao.limite-eventos}") int limiteEventos) {
        this.penalidadeNoShow = penalidadeNoShow;
        this.limiteEventos = limiteEventos;
    }

    /** Quanto custa faltar, em valor absoluto - usado tambem no log do
     *  {@link NoShowSchedulerJob}, que precisa dizer o numero certo. */
    public int penalidadeNoShow() {
        return penalidadeNoShow;
    }

    /** Teto de pontos ganhos em eventos. */
    public int limiteEventos() {
        return limiteEventos;
    }

    /**
     * @param inscricao inscricao do aluno no evento
     * @param checkin   check-in dele, ou {@code null} se nao houver
     * @return o resultado, ou {@code null} se o evento ainda nao deve
     *         entrar na conta (nao terminou)
     */
    Resultado avaliar(Evento evento, Inscricao inscricao, Checkin checkin, LocalDateTime agora) {
        if (inscricao.getStatus() == InscricaoStatus.CANCELADA) {
            return new Resultado(0, CANCELADO);
        }

        // Evento em andamento ou futuro nao pontua nem penaliza: o aluno
        // ainda pode aparecer.
        if (evento.getDataHoraFim().isAfter(agora)) {
            return null;
        }

        if (checkin == null) {
            return new Resultado(-penalidadeNoShow, NO_SHOW);
        }
        if (checkin.isPresencaQualificada()) {
            return new Resultado(evento.getPontos(), CONCLUIDO);
        }
        // Marcou a entrada e esqueceu a saida: nao ganha os pontos, mas
        // tambem nao e tratado como quem faltou - ele esteve la.
        return new Resultado(0, CONCLUIDO);
    }

    /** Acumulador que aplica o teto no fechamento da conta. */
    Totalizador totalizador() {
        return new Totalizador(limiteEventos);
    }

    /**
     * Soma os resultados de um aluno separando ganhos de penalidades,
     * porque o teto vale so para os ganhos.
     *
     * <p>Nao e thread-safe nem precisa ser: cada aluno tem o seu, dentro
     * de uma unica requisicao.
     */
    static final class Totalizador {

        private final int limite;
        private int ganhos;
        private int penalidades;

        private Totalizador(int limite) {
            this.limite = limite;
        }

        void somar(Resultado resultado) {
            if (resultado.pontos() >= 0) {
                ganhos += resultado.pontos();
            } else {
                penalidades += resultado.pontos();
            }
        }

        /** Ganhos aparados pelo teto, menos as penalidades por inteiro. */
        int total() {
            return Math.min(ganhos, limite) + penalidades;
        }

        /** Quanto do que o aluno ganhou foi cortado pelo teto - a tela de
         *  pontuacao mostra isso, senao o aluno soma os itens a mao, da
         *  diferente do total e acha que o sistema errou. */
        int excedente() {
            return Math.max(0, ganhos - limite);
        }
    }

    record Resultado(int pontos, String status) {
    }
}

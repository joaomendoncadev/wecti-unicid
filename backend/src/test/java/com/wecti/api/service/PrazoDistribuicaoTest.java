package com.wecti.api.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Janela de lancamento da pontuacao: 26/10/2026 a 06/12/2026.
 *
 * <p>O que estes testes protegem e a borda, que e onde dar errado custa
 * caro: fechar um dia cedo tira a chance de quem deixou para a ultima
 * hora - justamente quem mais precisa dela.
 */
class PrazoDistribuicaoTest {

    /** Mesmas datas do application.yml. */
    private final PrazoDistribuicao prazo = new PrazoDistribuicao(
            LocalDate.of(2026, 10, 26), LocalDate.of(2026, 12, 6));

    @Test
    @DisplayName("no meio da janela, aberto")
    void meioDaJanela() {
        assertThat(prazo.estaAbertoEm(LocalDateTime.of(2026, 11, 2, 14, 30))).isTrue();
    }

    /** "26/10 a 06/12" inclui os dois extremos. */
    @Test
    @DisplayName("o primeiro dia abre a 00:00")
    void primeiroDia() {
        assertThat(prazo.estaAbertoEm(LocalDateTime.of(2026, 10, 26, 0, 0))).isTrue();
        assertThat(prazo.estaAbertoEm(LocalDateTime.of(2026, 10, 25, 23, 59, 59))).isFalse();
    }

    /**
     * A borda que mais erra na mao: "ate 06/12" e o dia 06 INTEIRO. Se
     * fechasse a 00:00 de 06/12, o aluno perderia um dia inteiro de
     * prazo sem nunca ter sido avisado disso.
     */
    @Test
    @DisplayName("o ultimo dia vale ate 23:59:59")
    void ultimoDia() {
        assertThat(prazo.estaAbertoEm(LocalDateTime.of(2026, 12, 6, 0, 0))).isTrue();
        assertThat(prazo.estaAbertoEm(LocalDateTime.of(2026, 12, 6, 12, 0))).isTrue();
        assertThat(prazo.estaAbertoEm(LocalDateTime.of(2026, 12, 6, 23, 59, 59))).isTrue();
    }

    @Test
    @DisplayName("fecha na virada para 07/12")
    void depoisDoFim() {
        assertThat(prazo.estaAbertoEm(LocalDateTime.of(2026, 12, 7, 0, 0))).isFalse();
        assertThat(prazo.estaAbertoEm(LocalDateTime.of(2026, 12, 20, 10, 0))).isFalse();
    }

    /** O periodo tem inicio tambem, nao so fim: antes de 26/10 nao ha o
     *  que distribuir, porque o evento nem foi apurado. */
    @Test
    @DisplayName("antes do inicio, fechado")
    void antesDoInicio() {
        assertThat(prazo.estaAbertoEm(LocalDateTime.of(2026, 9, 30, 18, 0))).isFalse();
    }

    @Test
    @DisplayName("expoe as datas para a tela mostrar o periodo")
    void exponeDatas() {
        assertThat(prazo.inicio()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(prazo.fim()).isEqualTo(LocalDate.of(2026, 12, 6));
    }
}

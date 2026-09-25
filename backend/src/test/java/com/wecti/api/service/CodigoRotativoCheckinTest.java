package com.wecti.api.service;

import com.wecti.api.domain.SessaoCheckin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O QR de check-in fica projetado na tela e e o mesmo pra sala inteira -
 * nao existe versao individual. Enquanto o link era fixo, um aluno
 * presente podia fotografar a tela, mandar no grupo, e quem nao veio
 * marcava presenca de casa.
 *
 * O codigo rotativo e a trava contra isso, e o teste central e o de que
 * um codigo de janela passada REALMENTE para de ser aceito - sem essa
 * garantia, tudo o mais aqui e enfeite.
 */
class CodigoRotativoCheckinTest {

    private static final long JANELA_SEGUNDOS = 60;
    private static final Instant T0 = Instant.parse("2026-08-27T14:00:00Z");
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    /** Relogio que a gente empurra a mao, pra andar no tempo sem sleep. */
    private static class RelogioAjustavel extends Clock {
        private Instant agora = T0;

        @Override public ZoneId getZone() { return FUSO; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return agora; }

        void avancar(Duration quanto) { agora = agora.plus(quanto); }
    }

    private final RelogioAjustavel relogio = new RelogioAjustavel();
    private final CodigoRotativoCheckin codigos = new CodigoRotativoCheckin(JANELA_SEGUNDOS, relogio);

    private SessaoCheckin sessao(String segredo) {
        return SessaoCheckin.builder().segredo(segredo).build();
    }

    private SessaoCheckin sessao() {
        return sessao(codigos.gerarSegredo());
    }

    @Test
    @DisplayName("codigo de uma janela antiga nao vale mais - e o que impede o print no grupo")
    void codigoAntigoNaoEAceito() {
        SessaoCheckin sessao = sessao();
        String printDaTela = codigos.codigoAtual(sessao);

        // Tempo que leva pra fotografar, mandar no grupo e o outro abrir.
        relogio.avancar(Duration.ofMinutes(3));

        assertThat(codigos.aceita(sessao, printDaTela))
                .as("um link repassado depois de alguns minutos nao pode valer presenca")
                .isFalse();
    }

    @Test
    @DisplayName("codigo da janela atual e aceito")
    void codigoAtualEAceito() {
        SessaoCheckin sessao = sessao();

        assertThat(codigos.aceita(sessao, codigos.codigoAtual(sessao))).isTrue();
    }

    @Test
    @DisplayName("codigo da janela anterior ainda e aceito: quem escaneou 1s antes da troca nao pode ser punido")
    void janelaAnteriorTemFolga() {
        SessaoCheckin sessao = sessao();
        String codigo = codigos.codigoAtual(sessao);

        relogio.avancar(Duration.ofSeconds(JANELA_SEGUNDOS));

        assertThat(codigos.aceita(sessao, codigo))
                .as("sem essa folga, escanear em cima da virada quebraria um check-in legitimo")
                .isTrue();
    }

    @Test
    @DisplayName("mas duas janelas atras ja nao vale")
    void duasJanelasAtrasNaoVale() {
        SessaoCheckin sessao = sessao();
        String codigo = codigos.codigoAtual(sessao);

        relogio.avancar(Duration.ofSeconds(JANELA_SEGUNDOS * 2));

        assertThat(codigos.aceita(sessao, codigo)).isFalse();
    }

    @Test
    @DisplayName("o codigo muda quando a janela vira")
    void codigoMudaEntreJanelas() {
        SessaoCheckin sessao = sessao();
        String antes = codigos.codigoAtual(sessao);

        relogio.avancar(Duration.ofSeconds(JANELA_SEGUNDOS));

        assertThat(codigos.codigoAtual(sessao)).isNotEqualTo(antes);
    }

    @Test
    @DisplayName("dentro da mesma janela o codigo e estavel")
    void codigoEstavelDentroDaJanela() {
        SessaoCheckin sessao = sessao();
        String primeiro = codigos.codigoAtual(sessao);

        relogio.avancar(Duration.ofSeconds(JANELA_SEGUNDOS / 2));

        assertThat(codigos.codigoAtual(sessao))
                .as("a tela do admin busca o QR varias vezes; nao pode mudar no meio da janela")
                .isEqualTo(primeiro);
    }

    @Test
    @DisplayName("codigo de um evento nao vale em outro")
    void codigoNaoAtravessaSessoes() {
        SessaoCheckin palestraA = sessao();
        SessaoCheckin palestraB = sessao();

        assertThat(codigos.aceita(palestraB, codigos.codigoAtual(palestraA)))
                .as("segredos diferentes por sessao: um QR nao pode servir pra outro evento")
                .isFalse();
    }

    @Test
    @DisplayName("codigo ausente ou vazio e recusado")
    void codigoAusenteERecusado() {
        SessaoCheckin sessao = sessao();

        assertThat(codigos.aceita(sessao, null)).isFalse();
        assertThat(codigos.aceita(sessao, "")).isFalse();
        assertThat(codigos.aceita(sessao, "   ")).isFalse();
    }

    @Test
    @DisplayName("codigo chutado e recusado")
    void codigoChutadoERecusado() {
        SessaoCheckin sessao = sessao();

        assertThat(codigos.aceita(sessao, "0000000000")).isFalse();
        assertThat(codigos.aceita(sessao, "nao-e-um-codigo")).isFalse();
    }

    @Test
    @DisplayName("segredo e diferente a cada sessao")
    void segredoENovoACadaSessao() {
        Set<String> segredos = new HashSet<>();
        IntStream.range(0, 200).forEach(i -> segredos.add(codigos.gerarSegredo()));

        assertThat(segredos)
                .as("segredo repetido faria o codigo de um evento valer em outro")
                .hasSize(200);
    }

    @Test
    @DisplayName("fim da janela cai sempre no proximo multiplo da janela")
    void fimDaJanelaEOInstanteDaProximaTroca() {
        // T0 e 14:00:00Z exato, entao a proxima troca e 60s depois.
        assertThat(codigos.fimDaJanelaAtual())
                .isEqualTo(T0.plusSeconds(JANELA_SEGUNDOS).atZone(FUSO).toLocalDateTime());

        relogio.avancar(Duration.ofSeconds(25));

        assertThat(codigos.fimDaJanelaAtual())
                .as("a tela precisa buscar o proximo QR na virada, nao 60s depois de perguntar")
                .isEqualTo(T0.plusSeconds(JANELA_SEGUNDOS).atZone(FUSO).toLocalDateTime());
    }

    // ---------- os 15 minutos pedidos pelo professor ----------

    /** 900s = 15 min, o valor que o application.yml traz como padrao. */
    private static final long QUINZE_MINUTOS = 900;

    @Test
    @DisplayName("o aluno tem SEMPRE pelo menos 15 minutos, mesmo fotografando 1s antes da troca")
    void minimoGarantidoDeQuinzeMinutos() {
        // As janelas sao alinhadas ao relogio, entao o pior instante
        // possivel para fotografar e um segundo antes da virada: e ali
        // que o codigo tem a menor sobrevida.
        RelogioAjustavel r = new RelogioAjustavel();
        CodigoRotativoCheckin c = new CodigoRotativoCheckin(QUINZE_MINUTOS, r);
        r.avancar(Duration.ofSeconds(QUINZE_MINUTOS - 1));   // T0 e multiplo de 900

        SessaoCheckin sessao = sessao(c.gerarSegredo());
        String fotoDaTela = c.codigoAtual(sessao);

        r.avancar(Duration.ofMinutes(15));

        assertThat(c.aceita(sessao, fotoDaTela))
                .as("era isto que o professor pediu: 15 minutos de validade, "
                        + "sem depender de quando o aluno escaneou")
                .isTrue();
    }

    @Test
    @DisplayName("passados 30 minutos o codigo morre, mesmo no melhor caso")
    void tetoDeTrintaMinutos() {
        RelogioAjustavel r = new RelogioAjustavel();
        CodigoRotativoCheckin c = new CodigoRotativoCheckin(QUINZE_MINUTOS, r);
        // T0 e o inicio exato de uma janela: o melhor caso possivel.
        SessaoCheckin sessao = sessao(c.gerarSegredo());
        String codigo = c.codigoAtual(sessao);

        r.avancar(Duration.ofMinutes(29));
        assertThat(c.aceita(sessao, codigo))
                .as("aceita a janela atual e a anterior - no melhor caso sao quase 30 min")
                .isTrue();

        r.avancar(Duration.ofMinutes(2));   // 31 min no total
        assertThat(c.aceita(sessao, codigo))
                .as("o link vazado precisa morrer em algum momento")
                .isFalse();
    }
}

package com.wecti.api.service;

import com.wecti.api.domain.Checkin;
import com.wecti.api.domain.Evento;
import com.wecti.api.domain.Inscricao;
import com.wecti.api.domain.InscricaoStatus;
import com.wecti.api.domain.PontuacaoExtra;
import com.wecti.api.domain.Usuario;
import com.wecti.api.repository.CheckinRepository;
import com.wecti.api.repository.EventoRepository;
import com.wecti.api.repository.InscricaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Pontuacao do aluno. Nao ha tabela de saldo: o valor e
 * recalculado a cada consulta a partir de Inscricao/Checkin/Evento, entao
 * estes testes cobrem a regra inteira, nao um cache.
 *
 * Regras cobertas:
 *  - so pontua quem cumpre o MESMO criterio do certificado (entrada e
 *    saida lidas - a exigencia de 75% de permanencia saiu em setembro
 *    de 2026);
 *  - quem nao cancelou e nao compareceu leva a penalidade FIXA (no-show);
 *  - inscricao cancelada nao pontua nem penaliza;
 *  - evento que ainda nao terminou nao entra na conta;
 *  - o teto apara so os ganhos, e a penalidade e descontada depois dele;
 *  - gincana fica fora do teto.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PontuacaoServiceTest {

    private static final UUID ALUNO_ID = UUID.randomUUID();
    /** Propositalmente DIFERENTE de PENALIDADE_NO_SHOW: com os dois em 100
     *  (como era antes), um teste de no-show passaria tanto com a regra
     *  nova - penalidade fixa - quanto com a antiga, que descontava os
     *  pontos do proprio evento. Valores distintos separam as duas. */
    private static final int PONTOS_DO_EVENTO = 300;
    private static final int PENALIDADE_NO_SHOW = 100;
    /** Teto baixo de proposito: da para montar o cenario com 3 eventos
     *  em vez de 21. A regra e a mesma em qualquer valor. */
    private static final int LIMITE_EVENTOS = 500;

    @Mock private EventoRepository eventoRepository;
    @Mock private InscricaoRepository inscricaoRepository;
    @Mock private CheckinRepository checkinRepository;
    @Mock private PontuacaoExtraService pontuacaoExtraService;

    // Regra real, e nao mock: e exatamente a conta sob exame aqui.
    @Spy private RegraPontuacao regraPontuacao = new RegraPontuacao(PENALIDADE_NO_SHOW, LIMITE_EVENTOS);

    @InjectMocks private PontuacaoService pontuacaoService;

    @BeforeEach
    void preparar() {
        // Sem pontos de gincana nos cenarios deste teste: aqui a conta
        // sob exame e a das palestras. Os extras tem teste proprio.
        when(pontuacaoExtraService.listar(any())).thenReturn(List.of());
    }

    /** Evento de 2h que ja terminou ontem. */
    private Evento eventoEncerrado() {
        LocalDateTime inicio = LocalDateTime.now().minusDays(1).withHour(19).withMinute(0);
        return Evento.builder()
                .id(UUID.randomUUID())
                                .titulo("Palestra de teste")
                .dataHoraInicio(inicio)
                .dataHoraFim(inicio.plusHours(2))
                .pontos(PONTOS_DO_EVENTO)
                .build();
    }

    private Inscricao inscricao(Evento evento, InscricaoStatus status) {
        return Inscricao.builder()
                .id(UUID.randomUUID())
                .aluno(Usuario.builder().id(ALUNO_ID).build())
                .evento(evento)
                .status(status)
                .build();
    }

    private void cenario(Evento evento, Inscricao inscricao, Checkin checkin) {
        when(eventoRepository.findAll()).thenReturn(List.of(evento));
        when(inscricaoRepository.findByAlunoIdAndEventoId(ALUNO_ID, evento.getId()))
                .thenReturn(Optional.ofNullable(inscricao));
        when(checkinRepository.findByInscricaoId(any())).thenReturn(Optional.ofNullable(checkin));
    }

    @Test
    @DisplayName("presenca completa credita os pontos do evento")
    void presencaCompletaPontua() {
        Evento evento = eventoEncerrado();
        Inscricao inscricao = inscricao(evento, InscricaoStatus.ATIVA);
        Checkin checkin = Checkin.builder()
                .inscricao(inscricao)
                .entrada(evento.getDataHoraInicio())
                .saida(evento.getDataHoraFim())
                .build();
        cenario(evento, inscricao, checkin);

        var resultado = pontuacaoService.calcular(ALUNO_ID);

        assertThat(resultado.pontosTotal()).isEqualTo(PONTOS_DO_EVENTO);
        assertThat(resultado.eventos()).singleElement()
                .satisfies(item -> {
                    assertThat(item.pontos()).isEqualTo(PONTOS_DO_EVENTO);
                    assertThat(item.status()).isEqualTo("concluido");
                });
    }

    @Test
    @DisplayName("so check-in, sem check-out, nao pontua - mas tambem nao penaliza")
    void semCheckoutNaoPontua() {
        Evento evento = eventoEncerrado();
        Inscricao inscricao = inscricao(evento, InscricaoStatus.ATIVA);
        Checkin checkin = Checkin.builder()
                .inscricao(inscricao)
                .entrada(evento.getDataHoraInicio())
                .saida(null)
                .build();
        cenario(evento, inscricao, checkin);

        var resultado = pontuacaoService.calcular(ALUNO_ID);

        assertThat(resultado.pontosTotal()).isZero();
        assertThat(resultado.eventos()).singleElement()
                .satisfies(item -> assertThat(item.pontos()).isZero());
    }

    @Test
    @DisplayName("saiu cedo mas leu os dois QRs: PONTUA (nao ha mais minimo de permanencia)")
    void saidaAntecipadaPontua() {
        Evento evento = eventoEncerrado();
        Inscricao inscricao = inscricao(evento, InscricaoStatus.ATIVA);
        Checkin checkin = Checkin.builder()
                .inscricao(inscricao)
                .entrada(evento.getDataHoraInicio())
                .saida(evento.getDataHoraInicio().plusMinutes(30)) // 30min de 2h
                .build();
        cenario(evento, inscricao, checkin);

        var resultado = pontuacaoService.calcular(ALUNO_ID);

        assertThat(resultado.pontosTotal())
                .as("na regra antiga isso era 25%% e reprovava; o professor tirou a "
                        + "exigencia de permanencia em setembro de 2026")
                .isEqualTo(PONTOS_DO_EVENTO);
    }

    @Test
    @DisplayName("no-show: inscrito, evento encerrado e nenhum check-in - perde a penalidade FIXA")
    void noShowPenaliza() {
        Evento evento = eventoEncerrado();   // vale 300
        Inscricao inscricao = inscricao(evento, InscricaoStatus.ATIVA);
        cenario(evento, inscricao, null);

        var resultado = pontuacaoService.calcular(ALUNO_ID);

        assertThat(resultado.pontosTotal())
                .as("faltar custa um valor fixo, nao o que aquela palestra valia")
                .isEqualTo(-PENALIDADE_NO_SHOW)
                .isNotEqualTo(-PONTOS_DO_EVENTO);
        assertThat(resultado.eventos()).singleElement()
                .satisfies(item -> assertThat(item.status()).isEqualTo("no_show"));
    }

    @Test
    @DisplayName("faltar custa o mesmo numa palestra barata e numa cara")
    void noShowCustaIgualEmQualquerEvento() {
        Evento barata = eventoEncerrado();
        barata.setPontos(10);
        Inscricao inscricaoBarata = inscricao(barata, InscricaoStatus.ATIVA);
        cenario(barata, inscricaoBarata, null);
        int perdaNaBarata = pontuacaoService.calcular(ALUNO_ID).pontosTotal();

        Evento cara = eventoEncerrado();
        cara.setPontos(1000);
        Inscricao inscricaoCara = inscricao(cara, InscricaoStatus.ATIVA);
        cenario(cara, inscricaoCara, null);
        int perdaNaCara = pontuacaoService.calcular(ALUNO_ID).pontosTotal();

        assertThat(perdaNaBarata)
                .as("era o motivo da mudanca: quem reservava vaga na palestra concorrida "
                        + "e nao ia era quem mais perdia, sendo o desperdicio o mesmo")
                .isEqualTo(perdaNaCara)
                .isEqualTo(-PENALIDADE_NO_SHOW);
    }

    @Test
    @DisplayName("com a penalidade em ZERO (o valor de hoje), faltar nao tira ponto")
    void noShowComPenalidadeZeroNaoTiraPonto() {
        // Os outros testes de no-show injetam 100 de proposito, para
        // exercitar a mecanica do desconto. Este usa o valor que esta
        // valendo de verdade desde setembro de 2026.
        PontuacaoService semPenalidade = new PontuacaoService(eventoRepository, inscricaoRepository,
                checkinRepository, pontuacaoExtraService, new RegraPontuacao(0, LIMITE_EVENTOS));

        Evento evento = eventoEncerrado();
        cenario(evento, inscricao(evento, InscricaoStatus.ATIVA), null);

        var resultado = semPenalidade.calcular(ALUNO_ID);

        assertThat(resultado.pontosTotal()).isZero();
        assertThat(resultado.eventos()).singleElement()
                .satisfies(item -> {
                    assertThat(item.pontos()).isZero();
                    assertThat(item.status())
                            .as("o evento continua marcado como nao comparecido - o registro "
                                    + "serve para o admin, so nao custa mais pontos")
                            .isEqualTo("no_show");
                });
    }

    @Test
    @DisplayName("quem cancelou a tempo nao pontua nem e penalizado")
    void canceladaNaoPenaliza() {
        Evento evento = eventoEncerrado();
        Inscricao inscricao = inscricao(evento, InscricaoStatus.CANCELADA);
        cenario(evento, inscricao, null);

        var resultado = pontuacaoService.calcular(ALUNO_ID);

        assertThat(resultado.pontosTotal())
                .as("cancelar e diferente de faltar - nao pode virar no-show")
                .isZero();
        assertThat(resultado.eventos()).singleElement()
                .satisfies(item -> assertThat(item.status()).isEqualTo("cancelado"));
    }

    @Test
    @DisplayName("evento que ainda nao terminou nao entra na conta")
    void eventoFuturoNaoConta() {
        LocalDateTime inicio = LocalDateTime.now().plusDays(3);
        Evento evento = Evento.builder()
                .id(UUID.randomUUID()).titulo("Ainda vai acontecer")
                .dataHoraInicio(inicio).dataHoraFim(inicio.plusHours(2))
                .pontos(PONTOS_DO_EVENTO).build();
        Inscricao inscricao = inscricao(evento, InscricaoStatus.ATIVA);
        cenario(evento, inscricao, null);

        var resultado = pontuacaoService.calcular(ALUNO_ID);

        assertThat(resultado.pontosTotal())
                .as("inscrito num evento futuro nao pode ser tratado como faltoso")
                .isZero();
        assertThat(resultado.eventos()).isEmpty();
    }

    @Test
    @DisplayName("evento em que o aluno nem se inscreveu e ignorado")
    void semInscricaoIgnora() {
        Evento evento = eventoEncerrado();
        cenario(evento, null, null);

        var resultado = pontuacaoService.calcular(ALUNO_ID);

        assertThat(resultado.pontosTotal()).isZero();
        assertThat(resultado.eventos()).isEmpty();
    }

    // ── Teto de pontos em eventos ──────────────────────────────────────

    @Test
    @DisplayName("abaixo do teto nada e cortado")
    void abaixoDoTetoNaoCorta() {
        cenarioCom(1, PONTOS_DO_EVENTO, 0);   // 300 de 500

        var resultado = pontuacaoService.calcular(ALUNO_ID);

        assertThat(resultado.pontosEventos()).isEqualTo(PONTOS_DO_EVENTO);
        assertThat(resultado.pontosEventosExcedente()).isZero();
    }

    @Test
    @DisplayName("ganhos acima do teto param no teto, e o excedente aparece na resposta")
    void tetoAparaOsGanhos() {
        cenarioCom(3, PONTOS_DO_EVENTO, 0);   // 900 ganhos, teto 500

        var resultado = pontuacaoService.calcular(ALUNO_ID);

        assertThat(resultado.pontosEventos()).isEqualTo(LIMITE_EVENTOS);
        assertThat(resultado.limiteEventos()).isEqualTo(LIMITE_EVENTOS);
        assertThat(resultado.pontosEventosExcedente())
                .as("o aluno precisa conseguir explicar por que a soma dos itens "
                        + "da diferente do total")
                .isEqualTo(900 - LIMITE_EVENTOS);
        assertThat(resultado.eventos())
                .as("cada item continua mostrando o que aquela palestra valeu - o "
                        + "teto e do conjunto, nao de um evento")
                .hasSize(3)
                .allSatisfy(item -> assertThat(item.pontos()).isEqualTo(PONTOS_DO_EVENTO));
    }

    @Test
    @DisplayName("a falta desconta DEPOIS do teto - quem ja estourou o teto nao falta de graca")
    void penalidadeDescontaDepoisDoTeto() {
        cenarioCom(3, PONTOS_DO_EVENTO, 1);   // 900 ganhos + 1 falta

        var resultado = pontuacaoService.calcular(ALUNO_ID);

        assertThat(resultado.pontosEventos())
                .as("se o teto fosse aplicado sobre o saldo, (900-100)=800 tambem "
                        + "daria 500 e a falta teria sumido")
                .isEqualTo(LIMITE_EVENTOS - PENALIDADE_NO_SHOW);
    }

    @Test
    @DisplayName("gincana fica fora do teto de eventos")
    void gincanaNaoEntraNoTeto() {
        cenarioCom(3, PONTOS_DO_EVENTO, 0);   // eventos travados em 500
        when(pontuacaoExtraService.listar(ALUNO_ID)).thenReturn(List.of(extraDe(250)));

        var resultado = pontuacaoService.calcular(ALUNO_ID);

        assertThat(resultado.pontosEventos()).isEqualTo(LIMITE_EVENTOS);
        assertThat(resultado.pontosExtras()).isEqualTo(250);
        assertThat(resultado.pontosTotal())
                .as("premiacao de gincana e lancada a mao pelo admin, nao e "
                        + "pontuacao de palestra - o teto nao a alcanca")
                .isEqualTo(LIMITE_EVENTOS + 250);
    }

    /**
     * Monta N eventos encerrados em que o aluno esteve presente do inicio
     * ao fim, mais M em que ele se inscreveu e nao apareceu.
     */
    private void cenarioCom(int presencas, int pontosCada, int faltas) {
        List<Evento> eventos = new ArrayList<>();
        Map<UUID, Inscricao> inscricaoPorEvento = new HashMap<>();
        Map<UUID, Checkin> checkinPorInscricao = new HashMap<>();

        for (int i = 0; i < presencas + faltas; i++) {
            Evento evento = eventoEncerrado();
            evento.setPontos(pontosCada);
            Inscricao inscricao = inscricao(evento, InscricaoStatus.ATIVA);
            eventos.add(evento);
            inscricaoPorEvento.put(evento.getId(), inscricao);
            if (i < presencas) {
                checkinPorInscricao.put(inscricao.getId(), Checkin.builder()
                        .inscricao(inscricao)
                        .entrada(evento.getDataHoraInicio())
                        .saida(evento.getDataHoraFim())
                        .build());
            }
        }

        when(eventoRepository.findAll()).thenReturn(eventos);
        when(inscricaoRepository.findByAlunoIdAndEventoId(eq(ALUNO_ID), any()))
                .thenAnswer(chamada -> Optional.ofNullable(
                        inscricaoPorEvento.get(chamada.getArgument(1, UUID.class))));
        when(checkinRepository.findByInscricaoId(any()))
                .thenAnswer(chamada -> Optional.ofNullable(
                        checkinPorInscricao.get(chamada.getArgument(0, UUID.class))));
    }

    private PontuacaoExtra extraDe(int pontos) {
        Usuario aluno = Usuario.builder().id(ALUNO_ID).nome("Aluno").build();
        return PontuacaoExtra.builder()
                .id(UUID.randomUUID())
                .aluno(aluno)
                .criadoPor(Usuario.builder().id(UUID.randomUUID()).nome("Admin").build())
                .pontos(pontos)
                .motivo("Gincana")
                .build();
    }
}

package com.wecti.api.service;

import com.wecti.api.domain.Checkin;
import com.wecti.api.domain.Evento;
import com.wecti.api.domain.Inscricao;
import com.wecti.api.domain.InscricaoStatus;
import com.wecti.api.domain.SessaoCheckin;
import com.wecti.api.domain.TipoSessaoCheckin;
import com.wecti.api.exception.RegraNegocioException;
import com.wecti.api.repository.EventoRepository;
import com.wecti.api.repository.InscricaoRepository;
import com.wecti.api.repository.SessaoCheckinRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * As duas travas contra o QR de check-in ser usado por quem nao esta na
 * sala:
 *
 *  - o codigo rotativo precisa bater (um link repassado no grupo vence
 *    junto com a janela em que foi tirado);
 *  - a sessao so vale dentro do horario do evento (antes era "criacao +
 *    6 horas", o que deixava um QR da manha valendo a tarde).
 *
 * Os horarios sao montados relativos a agora, entao os testes valem em
 * qualquer dia sem depender do relogio da maquina.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckinSessaoServiceTest {

    private static final long TOLERANCIA_ANTES = 30;
    private static final long TOLERANCIA_DEPOIS = 30;

    @Mock private SessaoCheckinRepository sessaoRepository;
    @Mock private EventoRepository eventoRepository;
    @Mock private InscricaoRepository inscricaoRepository;
    @Mock private CheckinService checkinService;

    private CodigoRotativoCheckin codigoRotativo;
    private CheckinSessaoService service;

    private final UUID eventoId = UUID.randomUUID();
    private final UUID alunoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        codigoRotativo = new CodigoRotativoCheckin(60, Clock.systemDefaultZone());
        service = new CheckinSessaoService(sessaoRepository, eventoRepository, inscricaoRepository,
                checkinService, codigoRotativo, TOLERANCIA_ANTES, TOLERANCIA_DEPOIS);

        when(sessaoRepository.save(any(SessaoCheckin.class))).thenAnswer(i -> i.getArgument(0));
        when(checkinService.registrarEntrada(any())).thenReturn(new Checkin());
    }

    /** Evento que comecou ha 10 minutos e termina daqui a 1 hora. */
    private Evento eventoAcontecendoAgora() {
        return evento(LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusHours(1));
    }

    private Evento evento(LocalDateTime inicio, LocalDateTime fim) {
        Evento evento = Evento.builder().titulo("Palestra").dataHoraInicio(inicio).dataHoraFim(fim).build();
        evento.setId(eventoId);
        return evento;
    }

    private SessaoCheckin sessaoDe(Evento evento) {
        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento));
        SessaoCheckin sessao = service.criar(eventoId, TipoSessaoCheckin.ENTRADA);
        sessao.setId(UUID.randomUUID());
        when(sessaoRepository.findById(sessao.getId())).thenReturn(Optional.of(sessao));
        return sessao;
    }

    private void alunoInscrito(InscricaoStatus status) {
        Inscricao inscricao = Inscricao.builder().status(status).build();
        when(inscricaoRepository.findByAlunoIdAndEventoId(alunoId, eventoId)).thenReturn(Optional.of(inscricao));
    }

    // ---------- codigo rotativo ----------

    @Test
    @DisplayName("confirma com o codigo do QR que esta na tela")
    void confirmaComCodigoDaJanelaAtual() {
        SessaoCheckin sessao = sessaoDe(eventoAcontecendoAgora());
        alunoInscrito(InscricaoStatus.ATIVA);

        service.confirmar(sessao.getId(), alunoId, codigoRotativo.codigoAtual(sessao));

        verify(checkinService).registrarEntrada(any());
    }

    @Test
    @DisplayName("recusa codigo de outra sessao - QR de um evento nao serve pra outro")
    void recusaCodigoDeOutraSessao() {
        SessaoCheckin sessao = sessaoDe(eventoAcontecendoAgora());
        SessaoCheckin outra = SessaoCheckin.builder().segredo(codigoRotativo.gerarSegredo()).build();
        alunoInscrito(InscricaoStatus.ATIVA);

        assertThatThrownBy(() -> service.confirmar(sessao.getId(), alunoId, codigoRotativo.codigoAtual(outra)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("mudou");

        verify(checkinService, never()).registrarEntrada(any());
    }

    @Test
    @DisplayName("recusa link sem codigo - e o formato do QR antigo, que qualquer um podia repassar")
    void recusaLinkSemCodigo() {
        SessaoCheckin sessao = sessaoDe(eventoAcontecendoAgora());
        alunoInscrito(InscricaoStatus.ATIVA);

        assertThatThrownBy(() -> service.confirmar(sessao.getId(), alunoId, null))
                .isInstanceOf(RegraNegocioException.class);

        verify(checkinService, never()).registrarEntrada(any());
    }

    @Test
    @DisplayName("o codigo e conferido ANTES da inscricao: sem QR valido nada acontece")
    void codigoEConferidoAntesDeTudo() {
        SessaoCheckin sessao = sessaoDe(eventoAcontecendoAgora());
        // aluno nem inscrito esta - mesmo assim o erro tem que ser o do QR
        when(inscricaoRepository.findByAlunoIdAndEventoId(alunoId, eventoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmar(sessao.getId(), alunoId, "codigo-chutado"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("mudou");
    }

    // ---------- janela do evento ----------

    @Test
    @DisplayName("sessao expira no fim do evento, nao 6 horas depois de criada")
    void sessaoExpiraComOEvento() {
        LocalDateTime fim = LocalDateTime.now().plusHours(1);
        SessaoCheckin sessao = sessaoDe(evento(LocalDateTime.now().minusMinutes(10), fim));

        assertThat(sessao.getExpiraEm())
                .as("antes era criacao + 6h, e um QR da manha valia a tarde inteira")
                .isEqualTo(fim.plusMinutes(TOLERANCIA_DEPOIS));
    }

    @Test
    @DisplayName("permite gerar o QR um pouco antes do evento comecar, pra projetar enquanto a sala enche")
    void permiteGerarDentroDaToleranciaDeAntes() {
        Evento evento = evento(LocalDateTime.now().plusMinutes(10), LocalDateTime.now().plusHours(2));
        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento));

        assertThat(service.criar(eventoId, TipoSessaoCheckin.ENTRADA)).isNotNull();
    }

    @Test
    @DisplayName("a abertura da janela segue a tolerancia configurada, e nao um valor fixo")
    void aberturaRespeitaAConfiguracao() {
        // Evento daqui a 45 minutos: fora de uma folga de 30, dentro de
        // uma de 60. E o unico jeito de o teste distinguir as duas - com
        // um evento em +10 min, qualquer uma das duas passaria.
        Evento evento = evento(LocalDateTime.now().plusMinutes(45), LocalDateTime.now().plusHours(2));
        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento));

        CheckinSessaoService com30 = servicoComToleranciaAntes(30);
        assertThatThrownBy(() -> com30.criar(eventoId, TipoSessaoCheckin.ENTRADA))
                .as("com folga de 30 min, faltando 45 para comecar ainda e cedo demais")
                .isInstanceOf(RegraNegocioException.class);

        CheckinSessaoService com60 = servicoComToleranciaAntes(60);
        assertThat(com60.criar(eventoId, TipoSessaoCheckin.ENTRADA))
                .as("com 60 - o valor pedido pelo professor - o mesmo evento ja libera o QR")
                .isNotNull();
    }

    private CheckinSessaoService servicoComToleranciaAntes(long minutos) {
        return new CheckinSessaoService(sessaoRepository, eventoRepository, inscricaoRepository,
                checkinService, codigoRotativo, minutos, TOLERANCIA_DEPOIS);
    }

    @Test
    @DisplayName("recusa gerar QR antes da janela abrir")
    void recusaGerarAntesDaJanela() {
        Evento evento = evento(LocalDateTime.now().plusHours(5), LocalDateTime.now().plusHours(7));
        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> service.criar(eventoId, TipoSessaoCheckin.ENTRADA))
                .as("melhor avisar o admin na hora do que deixar ele projetar um QR morto")
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("abre");
    }

    @Test
    @DisplayName("recusa gerar QR de evento que ja acabou")
    void recusaGerarDepoisDaJanela() {
        Evento evento = evento(LocalDateTime.now().minusHours(5), LocalDateTime.now().minusHours(4));
        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> service.criar(eventoId, TipoSessaoCheckin.ENTRADA))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("fechou");
    }

    @Test
    @DisplayName("recusa confirmar depois que a janela do evento fechou, mesmo com o codigo certo")
    void recusaConfirmarForaDaJanela() {
        // Sessao valida, criada durante o evento; depois o evento e
        // esticado pra tras, simulando o tempo tendo passado.
        Evento evento = eventoAcontecendoAgora();
        SessaoCheckin sessao = sessaoDe(evento);
        alunoInscrito(InscricaoStatus.ATIVA);

        sessao.setExpiraEm(LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> service.confirmar(sessao.getId(), alunoId, codigoRotativo.codigoAtual(sessao)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("fechou");

        verify(checkinService, never()).registrarEntrada(any());
    }

    // ---------- palestra que passa do horario ----------
    //
    // Pergunta do professor (setembro de 2026): "a palestra e das 9h as
    // 10h, o palestrante terminou 10h30 - como fica o check-out?". Os
    // testes abaixo respondem com o relogio em pontos diferentes depois
    // das 10h. A folga depois do fim e TOLERANCIA_DEPOIS (30 min, o mesmo
    // valor padrao de producao).

    /** Palestra "das 9h as 10h", vista com o relogio marcando 10h + N min. */
    private Evento palestraDas9As10(long minutosDepoisDoFim) {
        LocalDateTime fim = LocalDateTime.now().minusMinutes(minutosDepoisDoFim);
        return evento(fim.minusHours(1), fim);
    }

    /** QR de saida gerado pelo admin enquanto a palestra ainda rolava
     *  (10h15), como aconteceria na pratica. A validade dele e sempre
     *  fim + folga, nao importa quando foi gerado. */
    private SessaoCheckin qrDeSaidaGeradoAs10h15(Evento evento) {
        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento));
        SessaoCheckin sessao = SessaoCheckin.builder()
                .id(UUID.randomUUID())
                .evento(evento)
                .tipo(TipoSessaoCheckin.SAIDA)
                .segredo(codigoRotativo.gerarSegredo())
                .criadaEm(evento.getDataHoraFim().plusMinutes(15))
                .expiraEm(evento.getDataHoraFim().plusMinutes(TOLERANCIA_DEPOIS))
                .build();
        when(sessaoRepository.findById(sessao.getId())).thenReturn(Optional.of(sessao));
        return sessao;
    }

    @Test
    @DisplayName("palestra das 9h-10h que acabou 10h30: saida as 10h25 e aceita")
    void atrasoCheckoutDentroDaFolga() {
        SessaoCheckin sessao = qrDeSaidaGeradoAs10h15(palestraDas9As10(25));
        alunoInscrito(InscricaoStatus.ATIVA);

        service.confirmar(sessao.getId(), alunoId, codigoRotativo.codigoAtual(sessao));

        verify(checkinService).registrarSaida(any());
    }

    @Test
    @DisplayName("palestra das 9h-10h que acabou 10h30: saida as 10h29 ainda e aceita")
    void atrasoCheckoutNoLimite() {
        SessaoCheckin sessao = qrDeSaidaGeradoAs10h15(palestraDas9As10(29));
        alunoInscrito(InscricaoStatus.ATIVA);

        service.confirmar(sessao.getId(), alunoId, codigoRotativo.codigoAtual(sessao));

        verify(checkinService).registrarSaida(any());
    }

    @Test
    @DisplayName("palestra das 9h-10h que acabou 10h30: saida as 10h31 e RECUSADA")
    void atrasoCheckoutForaDaFolga() {
        SessaoCheckin sessao = qrDeSaidaGeradoAs10h15(palestraDas9As10(31));
        alunoInscrito(InscricaoStatus.ATIVA);

        assertThatThrownBy(() -> service.confirmar(sessao.getId(), alunoId, codigoRotativo.codigoAtual(sessao)))
                .as("a sessao vale ate 10h + 30 min de folga; quem sai pela porta "
                        + "depois disso nao consegue mais marcar a saida")
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("fechou");

        verify(checkinService, never()).registrarSaida(any());
    }

    @Test
    @DisplayName("palestra das 9h-10h que acabou 10h30: gerar o QR de saida as 10h35 e recusado")
    void atrasoAdminNaoGeraQrDepoisDaFolga() {
        Evento evento = palestraDas9As10(35);
        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> service.criar(eventoId, TipoSessaoCheckin.SAIDA))
                .as("se o admin deixar para projetar o QR de saida so no fim da "
                        + "palestra atrasada, ja nao da mais")
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("fechou");
    }

    // ---------- regras que ja existiam, pra nao regredirem ----------

    @Test
    @DisplayName("inscricao cancelada nao confirma presenca")
    void inscricaoCanceladaNaoConfirma() {
        SessaoCheckin sessao = sessaoDe(eventoAcontecendoAgora());
        alunoInscrito(InscricaoStatus.CANCELADA);

        assertThatThrownBy(() -> service.confirmar(sessao.getId(), alunoId, codigoRotativo.codigoAtual(sessao)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("cancelada");
    }

    @Test
    @DisplayName("cada sessao tem seu proprio segredo")
    void cadaSessaoTemSegredoProprio() {
        Evento evento = eventoAcontecendoAgora();
        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento));

        SessaoCheckin entrada = service.criar(eventoId, TipoSessaoCheckin.ENTRADA);
        SessaoCheckin saida = service.criar(eventoId, TipoSessaoCheckin.SAIDA);

        assertThat(entrada.getSegredo()).isNotBlank().isNotEqualTo(saida.getSegredo());
    }
}

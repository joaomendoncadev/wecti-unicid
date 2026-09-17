package com.wecti.api.service;

import com.wecti.api.domain.Checkin;
import com.wecti.api.domain.Evento;
import com.wecti.api.domain.Inscricao;
import com.wecti.api.domain.InscricaoStatus;
import com.wecti.api.domain.Usuario;
import com.wecti.api.dto.RankingItemResponse;
import com.wecti.api.repository.CheckinRepository;
import com.wecti.api.repository.EventoRepository;
import com.wecti.api.repository.InscricaoRepository;
import com.wecti.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Ranking do WECTI.
 *
 * <p>O ponto critico coberto aqui e a <b>concordancia com a tela
 * individual</b>: os dois usam {@link RegraPontuacao}, e um aluno
 * que visse um total no proprio perfil e outro no ranking perderia a
 * confianca na competicao inteira. Por isso os cenarios repetem as mesmas
 * situacoes de PontuacaoServiceTest (no-show, cancelado, evento em
 * andamento, teto de pontos) e conferem o numero final.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RankingServiceTest {

    /** Diferente da penalidade de proposito: com os dois em 100, o teste
     *  de no-show passaria com a regra antiga (descontar os pontos do
     *  evento) e com a nova (valor fixo), sem distinguir as duas. */
    private static final int PONTOS_DO_EVENTO = 300;
    private static final int PENALIDADE_NO_SHOW = 100;
    private static final int LIMITE_EVENTOS = 500;

    @Mock private EventoRepository eventoRepository;
    @Mock private InscricaoRepository inscricaoRepository;
    @Mock private CheckinRepository checkinRepository;
    @Mock private PontuacaoExtraService pontuacaoExtraService;
    @Mock private UsuarioRepository usuarioRepository;

    // Regra real (nao mock): o que este teste protege e justamente o
    // ranking usar a MESMA conta da tela individual.
    @Spy private RegraPontuacao regraPontuacao = new RegraPontuacao(PENALIDADE_NO_SHOW, LIMITE_EVENTOS);

    @InjectMocks private RankingService rankingService;

    private Evento eventoEncerrado;
    private final List<Inscricao> inscricoes = new ArrayList<>();
    private final List<Checkin> checkins = new ArrayList<>();

    @BeforeEach
    void preparar() {
        LocalDateTime inicio = LocalDateTime.now().minusDays(2);
        eventoEncerrado = Evento.builder()
                .id(UUID.randomUUID())
                .titulo("Palestra encerrada")
                .dataHoraInicio(inicio)
                .dataHoraFim(inicio.plusHours(2))
                .pontos(PONTOS_DO_EVENTO)
                .build();

        when(eventoRepository.findAll()).thenReturn(List.of(eventoEncerrado));
        when(inscricaoRepository.findTodasParaRanking()).thenReturn(inscricoes);
        when(checkinRepository.findTodosParaRanking()).thenReturn(checkins);
        when(pontuacaoExtraService.totaisPorAluno()).thenReturn(Map.of());
    }

    private Usuario aluno(String nome, String rgm) {
        return Usuario.builder().id(UUID.randomUUID()).nome(nome).rgm(rgm).curso("ADS").build();
    }

    /** Inscreve o aluno no evento encerrado com presenca integral. */
    private Usuario presencaCompleta(String nome, String rgm) {
        Usuario usuario = aluno(nome, rgm);
        Inscricao inscricao = inscrever(usuario, InscricaoStatus.ATIVA);
        checkins.add(Checkin.builder()
                .inscricao(inscricao)
                .entrada(eventoEncerrado.getDataHoraInicio())
                .saida(eventoEncerrado.getDataHoraFim())
                .build());
        return usuario;
    }

    private Inscricao inscrever(Usuario usuario, InscricaoStatus status) {
        Inscricao inscricao = Inscricao.builder()
                .id(UUID.randomUUID())
                .aluno(usuario)
                .evento(eventoEncerrado)
                .status(status)
                .build();
        inscricoes.add(inscricao);
        return inscricao;
    }

    @Test
    @DisplayName("ordena do maior para o menor total")
    void ordenaPorPontos() {
        Usuario comPresenca = presencaCompleta("Bruno", "11111111");
        Usuario faltou = aluno("Ana", "22222222");
        inscrever(faltou, InscricaoStatus.ATIVA);

        var ranking = rankingService.montar(false);

        assertThat(ranking.itens()).extracting(RankingItemResponse::alunoNome)
                .containsExactly(comPresenca.getNome(), faltou.getNome());
        assertThat(ranking.itens().get(0).pontosTotal()).isEqualTo(PONTOS_DO_EVENTO);
        assertThat(ranking.itens().get(1).pontosTotal())
                .as("nao cancelou e nao compareceu = no-show, e a penalidade e fixa")
                .isEqualTo(-PENALIDADE_NO_SHOW);
    }

    @Test
    @DisplayName("empate divide a posicao e a seguinte pula (1, 2, 2, 4)")
    void empateDivideAPosicao() {
        presencaCompleta("Ana", "11111111");
        presencaCompleta("Bruno", "22222222");
        presencaCompleta("Carla", "33333333");
        Usuario ultimo = aluno("Daniel", "44444444");
        inscrever(ultimo, InscricaoStatus.CANCELADA);

        var ranking = rankingService.montar(false);

        assertThat(ranking.itens()).extracting(RankingItemResponse::posicao)
                .containsExactly(1, 1, 1, 4);
    }

    @Test
    @DisplayName("soma os pontos de gincana ao total")
    void somaPontosExtras() {
        Usuario aluno = presencaCompleta("Ana", "11111111");
        when(pontuacaoExtraService.totaisPorAluno()).thenReturn(Map.of(aluno.getId(), 50));

        var item = rankingService.montar(false).itens().get(0);

        assertThat(item.pontosEventos()).isEqualTo(PONTOS_DO_EVENTO);
        assertThat(item.pontosExtras()).isEqualTo(50);
        assertThat(item.pontosTotal()).isEqualTo(PONTOS_DO_EVENTO + 50);
    }

    @Test
    @DisplayName("gincana pode virar a disputa")
    void gincanaMudaAOrdem() {
        Usuario semGincana = presencaCompleta("Ana", "11111111");
        Usuario comGincana = aluno("Bruno", "22222222");
        inscrever(comGincana, InscricaoStatus.CANCELADA);
        when(pontuacaoExtraService.totaisPorAluno()).thenReturn(Map.of(comGincana.getId(), 500));

        var ranking = rankingService.montar(false);

        assertThat(ranking.itens().get(0).alunoNome()).isEqualTo(comGincana.getNome());
        assertThat(ranking.itens().get(1).alunoNome()).isEqualTo(semGincana.getNome());
    }

    @Test
    @DisplayName("aluno que so tem pontos de gincana, sem inscricao, entra no ranking")
    void alunoSoComGincanaAparece() {
        Usuario premiado = aluno("Fora da lista", "99999999");
        when(pontuacaoExtraService.totaisPorAluno()).thenReturn(Map.of(premiado.getId(), 30));
        when(usuarioRepository.findAllById(List.of(premiado.getId()))).thenReturn(List.of(premiado));

        var ranking = rankingService.montar(false);

        assertThat(ranking.itens()).singleElement()
                .as("sumir de um ranking em que voce tem pontos e o pior erro possivel aqui")
                .satisfies(item -> {
                    assertThat(item.alunoNome()).isEqualTo(premiado.getNome());
                    assertThat(item.pontosTotal()).isEqualTo(30);
                });
    }

    @Test
    @DisplayName("RGM so vai para a visao do admin")
    void rgmSoParaAdmin() {
        presencaCompleta("Ana", "12345678");

        assertThat(rankingService.montar(true).itens().get(0).alunoRgm()).isEqualTo("12345678");
        assertThat(rankingService.montar(false).itens().get(0).alunoRgm())
                .as("hoje so o admin chega ao ranking (SecurityConfig), mas a versao "
                        + "sem RGM segue coberta: essa decisao ja mudou uma vez e, se "
                        + "o ranking voltar para o aluno, o RGM dos colegas nao pode ir junto")
                .isNull();
    }

    @Test
    @DisplayName("evento ainda em andamento nao pontua nem penaliza")
    void eventoEmAndamentoNaoConta() {
        Evento emAndamento = Evento.builder()
                .id(UUID.randomUUID())
                .titulo("Acontecendo agora")
                .dataHoraInicio(LocalDateTime.now().minusMinutes(30))
                .dataHoraFim(LocalDateTime.now().plusHours(1))
                .pontos(PONTOS_DO_EVENTO)
                .build();
        when(eventoRepository.findAll()).thenReturn(List.of(emAndamento));

        Usuario aluno = aluno("Ana", "11111111");
        inscricoes.add(Inscricao.builder()
                .id(UUID.randomUUID())
                .aluno(aluno)
                .evento(emAndamento)
                .status(InscricaoStatus.ATIVA)
                .build());

        var item = rankingService.montar(false).itens().get(0);

        assertThat(item.pontosTotal())
                .as("o aluno ainda pode aparecer - penalizar antes do fim seria injusto")
                .isZero();
    }

    @Test
    @DisplayName("conta quantas palestras o aluno realmente concluiu")
    void contaEventosConcluidos() {
        presencaCompleta("Ana", "11111111");

        var item = rankingService.montar(false).itens().get(0);

        assertThat(item.eventosConcluidos()).isEqualTo(1);
    }

    @Test
    @DisplayName("presenca parcial nao pontua e nao conta como palestra concluida")
    void presencaParcialNaoPontua() {
        Usuario aluno = aluno("Ana", "11111111");
        Inscricao inscricao = inscrever(aluno, InscricaoStatus.ATIVA);
        // Entrou e saiu com 30 minutos num evento de 2h = 25%.
        checkins.add(Checkin.builder()
                .inscricao(inscricao)
                .entrada(eventoEncerrado.getDataHoraInicio())
                .saida(eventoEncerrado.getDataHoraInicio().plusMinutes(30))
                .build());

        var item = rankingService.montar(false).itens().get(0);

        assertThat(item.pontosTotal()).isZero();
        assertThat(item.eventosConcluidos()).isZero();
    }

    @Test
    @DisplayName("inscricao cancelada nao pontua nem penaliza")
    void canceladaNaoPenaliza() {
        Usuario aluno = aluno("Ana", "11111111");
        inscrever(aluno, InscricaoStatus.CANCELADA);

        assertThat(rankingService.montar(false).itens().get(0).pontosTotal()).isZero();
    }

    @Test
    @DisplayName("o teto de pontos vale no ranking igual a tela individual")
    void tetoValeNoRanking() {
        Usuario aluno = aluno("Ana", "11111111");
        List<Evento> eventos = new ArrayList<>();
        for (int i = 0; i < 3; i++) {                       // 3 x 300 = 900
            LocalDateTime inicio = LocalDateTime.now().minusDays(2 + i);
            Evento evento = Evento.builder()
                    .id(UUID.randomUUID()).titulo("Palestra " + i)
                    .dataHoraInicio(inicio).dataHoraFim(inicio.plusHours(2))
                    .pontos(PONTOS_DO_EVENTO).build();
            eventos.add(evento);
            Inscricao inscricao = Inscricao.builder()
                    .id(UUID.randomUUID()).aluno(aluno).evento(evento)
                    .status(InscricaoStatus.ATIVA).build();
            inscricoes.add(inscricao);
            checkins.add(Checkin.builder()
                    .inscricao(inscricao)
                    .entrada(evento.getDataHoraInicio())
                    .saida(evento.getDataHoraFim())
                    .build());
        }
        when(eventoRepository.findAll()).thenReturn(eventos);

        var item = rankingService.montar(false).itens().get(0);

        assertThat(item.pontosEventos())
                .as("um total aqui diferente do que o aluno ve no proprio perfil "
                        + "derruba a confianca na competicao inteira")
                .isEqualTo(LIMITE_EVENTOS);
        assertThat(item.eventosConcluidos())
                .as("o teto corta pontos, nao a contagem de palestras assistidas")
                .isEqualTo(3);
    }
}

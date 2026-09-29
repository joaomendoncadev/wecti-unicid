package com.wecti.api.service;

import com.wecti.api.domain.Checkin;
import com.wecti.api.domain.Evento;
import com.wecti.api.domain.Inscricao;
import com.wecti.api.domain.InscricaoStatus;
import com.wecti.api.domain.Perfil;
import com.wecti.api.domain.Usuario;
import com.wecti.api.exception.CampoInvalidoException;
import com.wecti.api.exception.ConflitoException;
import com.wecti.api.exception.RegraNegocioException;
import com.wecti.api.repository.CheckinRepository;
import com.wecti.api.repository.InscricaoRepository;
import com.wecti.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
 * Admin afirmando presenca de quem participou e nao conseguiu ler o QR.
 *
 * <p>O que estes testes protegem nao e o lancamento em si - e a cadeia que
 * depende dele. Pontos, certificado e saida do no-show derivam todos de
 * {@code Checkin.isPresencaQualificada()}, que e {@code saida != null};
 * se esta classe gravar um check-in sem saida, o aluno continua sem
 * certificado e o admin acha que resolveu.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PresencaManualServiceTest {

    private static final UUID EVENTO_ID = UUID.randomUUID();
    private static final UUID ALUNO_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID INSCRICAO_ID = UUID.randomUUID();
    private static final String MOTIVO = "Camera do celular nao lia o QR - conferido na porta";

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private InscricaoRepository inscricaoRepository;
    @Mock private CheckinRepository checkinRepository;
    @Mock private EventoService eventoService;

    private PresencaManualService service;
    private Evento evento;
    private Usuario aluno;
    private Usuario admin;

    @BeforeEach
    void preparar() {
        service = new PresencaManualService(
                usuarioRepository, inscricaoRepository, checkinRepository, eventoService);

        // Palestra que ja terminou - o cenario real da compensacao.
        evento = Evento.builder()
                .id(EVENTO_ID)
                .titulo("Vibe Coding")
                .dataHoraInicio(LocalDateTime.now().minusHours(3))
                .dataHoraFim(LocalDateTime.now().minusHours(1))
                .pontos(300)
                .build();
        aluno = Usuario.builder().id(ALUNO_ID).nome("Aluno").perfil(Perfil.ALUNO).rgm("20000001").build();
        admin = Usuario.builder().id(ADMIN_ID).nome("Admin").perfil(Perfil.ADMIN).cpf("12345678901").build();

        when(eventoService.buscarPorId(EVENTO_ID)).thenReturn(evento);
        when(usuarioRepository.findById(ALUNO_ID)).thenReturn(Optional.of(aluno));
        when(usuarioRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(inscricaoRepository.save(any(Inscricao.class)))
                .thenAnswer(chamada -> chamada.getArgument(0));
        when(checkinRepository.save(any(Checkin.class)))
                .thenAnswer(chamada -> chamada.getArgument(0));
    }

    private Inscricao inscricaoAtiva() {
        return Inscricao.builder()
                .id(INSCRICAO_ID).aluno(aluno).evento(evento).status(InscricaoStatus.ATIVA).build();
    }

    private Checkin registrar() {
        return service.registrar(EVENTO_ID, ALUNO_ID, MOTIVO, ADMIN_ID);
    }

    @Nested
    @DisplayName("os tres casos de QR que falhou")
    class CasosDeQr {

        /** Nao leu QR nenhum: e o caso mais comum: celular sem camera
         *  funcionando, ou aluno travado fora do sistema. */
        @Test
        @DisplayName("sem QR nenhum: grava entrada e saida com o horario do evento")
        void semNenhumaLeitura() {
            when(inscricaoRepository.findByAlunoIdAndEventoId(ALUNO_ID, EVENTO_ID))
                    .thenReturn(Optional.of(inscricaoAtiva()));
            when(checkinRepository.findByInscricaoId(INSCRICAO_ID)).thenReturn(Optional.empty());

            Checkin checkin = registrar();

            assertThat(checkin.getEntrada()).isEqualTo(evento.getDataHoraInicio());
            assertThat(checkin.getSaida()).isEqualTo(evento.getDataHoraFim());
            assertThat(checkin.isPresencaQualificada())
                    .as("e o que libera pontos e certificado")
                    .isTrue();
        }

        /** Leu a entrada e perdeu a saida. A entrada real e dado
         *  verdadeiro - ele escaneou mesmo - e nao pode ser sobrescrita
         *  pelo horario do evento. */
        @Test
        @DisplayName("so a entrada lida: completa a saida e preserva a entrada real")
        void apenasEntradaLida() {
            LocalDateTime entradaReal = evento.getDataHoraInicio().plusMinutes(7);
            when(inscricaoRepository.findByAlunoIdAndEventoId(ALUNO_ID, EVENTO_ID))
                    .thenReturn(Optional.of(inscricaoAtiva()));
            when(checkinRepository.findByInscricaoId(INSCRICAO_ID)).thenReturn(Optional.of(
                    Checkin.builder().inscricao(inscricaoAtiva()).entrada(entradaReal).build()));

            Checkin checkin = registrar();

            assertThat(checkin.getEntrada())
                    .as("o horario que ele realmente escaneou")
                    .isEqualTo(entradaReal);
            assertThat(checkin.getSaida()).isEqualTo(evento.getDataHoraFim());
        }

        /** Entrada dentro da tolerancia, depois do fim programado: a
         *  saida nao pode ficar antes da entrada, senao a permanencia
         *  vira negativa e o percentual da lista quebra. */
        @Test
        @DisplayName("entrada depois do fim do evento nao gera permanencia negativa")
        void entradaDepoisDoFim() {
            LocalDateTime entradaTardia = evento.getDataHoraFim().plusMinutes(10);
            when(inscricaoRepository.findByAlunoIdAndEventoId(ALUNO_ID, EVENTO_ID))
                    .thenReturn(Optional.of(inscricaoAtiva()));
            when(checkinRepository.findByInscricaoId(INSCRICAO_ID)).thenReturn(Optional.of(
                    Checkin.builder().inscricao(inscricaoAtiva()).entrada(entradaTardia).build()));

            Checkin checkin = registrar();

            assertThat(checkin.getSaida()).isAfterOrEqualTo(checkin.getEntrada());
        }

        @Test
        @DisplayName("ja tem os dois: recusa, porque nao ha o que compensar")
        void jaTemPresencaCompleta() {
            when(inscricaoRepository.findByAlunoIdAndEventoId(ALUNO_ID, EVENTO_ID))
                    .thenReturn(Optional.of(inscricaoAtiva()));
            when(checkinRepository.findByInscricaoId(INSCRICAO_ID)).thenReturn(Optional.of(
                    Checkin.builder().inscricao(inscricaoAtiva())
                            .entrada(evento.getDataHoraInicio())
                            .saida(evento.getDataHoraFim()).build()));

            assertThatThrownBy(PresencaManualServiceTest.this::registrar)
                    .isInstanceOf(ConflitoException.class)
                    .hasMessageContaining("ja tem check-in e check-out");
        }
    }

    @Nested
    @DisplayName("a inscricao que falta")
    class InscricaoQueFalta {

        /** Parte dos alunos afetados nem chegou a se inscrever - estavam
         *  travados fora do sistema no dia da palestra. O prazo de
         *  inscricao ja fechou, e e o admin quem decide aqui. */
        @Test
        @DisplayName("cria a inscricao de quem nunca se inscreveu")
        void criaInscricaoInexistente() {
            when(inscricaoRepository.findByAlunoIdAndEventoId(ALUNO_ID, EVENTO_ID))
                    .thenReturn(Optional.empty());
            when(checkinRepository.findByInscricaoId(any())).thenReturn(Optional.empty());

            Checkin checkin = registrar();

            verify(inscricaoRepository).save(any(Inscricao.class));
            assertThat(checkin.getInscricao().getStatus()).isEqualTo(InscricaoStatus.ATIVA);
            assertThat(checkin.isPresencaQualificada()).isTrue();
        }

        /** Desistiu, mudou de ideia e compareceu. Insistir no
         *  cancelamento deixaria o admin sem saida pela aplicacao. */
        @Test
        @DisplayName("reativa a inscricao que estava cancelada")
        void reativaInscricaoCancelada() {
            Inscricao cancelada = Inscricao.builder()
                    .id(INSCRICAO_ID).aluno(aluno).evento(evento)
                    .status(InscricaoStatus.CANCELADA)
                    .canceladaEm(LocalDateTime.now().minusDays(1)).build();
            when(inscricaoRepository.findByAlunoIdAndEventoId(ALUNO_ID, EVENTO_ID))
                    .thenReturn(Optional.of(cancelada));
            when(checkinRepository.findByInscricaoId(INSCRICAO_ID)).thenReturn(Optional.empty());

            registrar();

            assertThat(cancelada.getStatus()).isEqualTo(InscricaoStatus.ATIVA);
            assertThat(cancelada.getCanceladaEm()).isNull();
        }
    }

    @Nested
    @DisplayName("as travas")
    class Travas {

        /** Sem isto, o admin que erra o evento na lista lanca presenca
         *  numa palestra que ainda vai acontecer. */
        @Test
        @DisplayName("recusa evento que ainda nao comecou")
        void eventoFuturo() {
            evento.setDataHoraInicio(LocalDateTime.now().plusHours(2));
            evento.setDataHoraFim(LocalDateTime.now().plusHours(4));

            assertThatThrownBy(PresencaManualServiceTest.this::registrar)
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("ainda nao comecou");
            verify(checkinRepository, never()).save(any());
        }

        @Test
        @DisplayName("recusa registrar presenca de um admin")
        void alvoNaoEAluno() {
            when(usuarioRepository.findById(ALUNO_ID)).thenReturn(Optional.of(admin));

            assertThatThrownBy(PresencaManualServiceTest.this::registrar)
                    .isInstanceOf(CampoInvalidoException.class);
            verify(checkinRepository, never()).save(any());
        }
    }

    /** A justificativa e a unica explicacao que vai sobrar depois para
     *  uma presenca sem leitura de QR. */
    @Test
    @DisplayName("guarda quem lancou e por que")
    void guardaAuditoria() {
        when(inscricaoRepository.findByAlunoIdAndEventoId(ALUNO_ID, EVENTO_ID))
                .thenReturn(Optional.of(inscricaoAtiva()));
        when(checkinRepository.findByInscricaoId(INSCRICAO_ID)).thenReturn(Optional.empty());

        Checkin checkin = service.registrar(EVENTO_ID, ALUNO_ID, "  " + MOTIVO + "  ", ADMIN_ID);

        assertThat(checkin.getRegistradoPor()).isEqualTo(admin);
        assertThat(checkin.getJustificativa()).isEqualTo(MOTIVO);
        assertThat(checkin.isRegistradoPeloAdmin())
                .as("a lista do admin marca essas linhas")
                .isTrue();
    }

    /**
     * A premissa do desenho inteiro: registrar a presenca faz a pontuacao
     * acontecer sozinha, sem nenhuma regra nova. Se um dia
     * {@link RegraPontuacao} passar a exigir outra coisa do check-in,
     * este teste quebra aqui - e nao na conferencia final da premiacao.
     */
    @Test
    @DisplayName("a presenca registrada vale os pontos do evento, dentro do teto")
    void presencaRegistradaVirapontos() {
        when(inscricaoRepository.findByAlunoIdAndEventoId(ALUNO_ID, EVENTO_ID))
                .thenReturn(Optional.of(inscricaoAtiva()));
        when(checkinRepository.findByInscricaoId(INSCRICAO_ID)).thenReturn(Optional.empty());

        Checkin checkin = registrar();

        // Mesma regra de producao (penalidade 0, teto 2000).
        var regra = new RegraPontuacao(0, 2000);
        var resultado = regra.avaliar(evento, checkin.getInscricao(), checkin, LocalDateTime.now());

        assertThat(resultado).isNotNull();
        assertThat(resultado.pontos())
                .as("os pontos da propria palestra, nao um valor lancado a mao")
                .isEqualTo(evento.getPontos());
        assertThat(resultado.status())
                .as("deixa de contar como no-show")
                .isEqualTo(RegraPontuacao.CONCLUIDO);
    }
}

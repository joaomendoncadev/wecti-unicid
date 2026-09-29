package com.wecti.api.service;

import com.wecti.api.domain.Checkin;
import com.wecti.api.domain.Evento;
import com.wecti.api.domain.Inscricao;
import com.wecti.api.domain.InscricaoStatus;
import com.wecti.api.domain.Perfil;
import com.wecti.api.domain.Usuario;
import com.wecti.api.exception.CampoInvalidoException;
import com.wecti.api.exception.ConflitoException;
import com.wecti.api.exception.RecursoNaoEncontradoException;
import com.wecti.api.exception.RegraNegocioException;
import com.wecti.api.repository.CheckinRepository;
import com.wecti.api.repository.InscricaoRepository;
import com.wecti.api.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * O admin afirma que um aluno participou da palestra, no lugar da leitura
 * do QR code que nao deu certo.
 *
 * <p><b>Por que registrar a presenca, e nao os pontos.</b> Tudo o que o
 * aluno perde quando o QR falha - pontos, certificado, sair da lista de
 * no-show - deriva de um unico fato: existir um {@link Checkin} com
 * entrada e saida. Gravar esse fato faz as quatro consequencias
 * acontecerem sozinhas, sem nenhuma regra nova:
 *
 * <ul>
 *   <li>{@link RegraPontuacao} ve {@code saida != null} e da os pontos do
 *       evento, ja respeitando o teto de {@code limite-eventos};</li>
 *   <li>{@link CertificadoService} encontra o check-in e emite o PDF
 *       normal;</li>
 *   <li>o evento deixa de contar como no-show;</li>
 *   <li>o aluno passa a aparecer na lista de presenca do admin.</li>
 * </ul>
 *
 * <p>O caminho alternativo - lancar os pontos a mao como
 * {@link PontuacaoExtraService} - resolveria so o primeiro item: o
 * certificado continuaria negado (ele nao olha pontuacao, olha check-in),
 * o aluno seguiria marcado como faltante, e os pontos furariam o teto de
 * 2000, porque pontuacao extra fica fora do teto de proposito, por ser
 * premiacao de gincana.
 *
 * <p><b>Os horarios sao os do evento</b>, nao o instante do clique: um
 * registro feito as 22h47 numa palestra que acabou as 21h gravaria uma
 * permanencia impossivel e sairia no certificado com a carga errada.
 */
@Service
public class PresencaManualService {

    private final UsuarioRepository usuarioRepository;
    private final InscricaoRepository inscricaoRepository;
    private final CheckinRepository checkinRepository;
    private final EventoService eventoService;

    public PresencaManualService(UsuarioRepository usuarioRepository, InscricaoRepository inscricaoRepository,
                                  CheckinRepository checkinRepository, EventoService eventoService) {
        this.usuarioRepository = usuarioRepository;
        this.inscricaoRepository = inscricaoRepository;
        this.checkinRepository = checkinRepository;
        this.eventoService = eventoService;
    }

    /**
     * @param justificativa obrigatoria - e a unica explicacao que vai
     *                      existir, depois, para uma presenca sem leitura
     *                      de QR
     * @param adminId       quem esta afirmando a presenca
     */
    @Transactional
    public Checkin registrar(UUID eventoId, UUID alunoId, String justificativa, UUID adminId) {
        Evento evento = eventoService.buscarPorId(eventoId);

        // Presenca so pode ser afirmada depois que a palestra comecou.
        // Antes disso nao ha o que confirmar, e o lancamento seria
        // presenca em evento que ainda nao aconteceu - tipicamente o
        // admin errando o evento na lista.
        if (evento.getDataHoraInicio().isAfter(LocalDateTime.now())) {
            throw new RegraNegocioException(
                    "Este evento ainda nao comecou - nao ha presenca para registrar.");
        }

        Usuario aluno = usuarioRepository.findById(alunoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aluno nao encontrado"));
        if (aluno.getPerfil() != Perfil.ALUNO) {
            throw new CampoInvalidoException("alunoId", "So e possivel registrar presenca de alunos");
        }
        Usuario admin = usuarioRepository.findById(adminId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuario nao encontrado"));

        Inscricao inscricao = garantirInscricao(aluno, evento);
        return completarCheckin(inscricao, evento, admin, justificativa.trim());
    }

    /**
     * Inscricao do aluno no evento, criada se nao existir.
     *
     * <p>Criar aqui ignora de proposito o {@link PrazoInscricao} - que a
     * essa altura ja fechou, porque a palestra comecou. Nao e um furo na
     * regra: o prazo existe para o ALUNO nao reservar vaga em cima da
     * hora, e quem decide aqui e o admin, sobre uma palestra que ja
     * aconteceu. Faz falta porque parte dos alunos afetados nem chegou a
     * se inscrever - estavam travados fora do sistema justamente no dia
     * da palestra.
     *
     * <p>Inscricao cancelada volta a valer: o aluno desistiu, mudou de
     * ideia e compareceu. Insistir no cancelamento deixaria o admin sem
     * saida pela aplicacao.
     */
    private Inscricao garantirInscricao(Usuario aluno, Evento evento) {
        Inscricao inscricao = inscricaoRepository
                .findByAlunoIdAndEventoId(aluno.getId(), evento.getId())
                .orElse(null);

        if (inscricao == null) {
            return inscricaoRepository.save(Inscricao.builder()
                    .aluno(aluno)
                    .evento(evento)
                    .status(InscricaoStatus.ATIVA)
                    .build());
        }
        if (inscricao.getStatus() == InscricaoStatus.CANCELADA) {
            inscricao.setStatus(InscricaoStatus.ATIVA);
            inscricao.setCanceladaEm(null);
            return inscricaoRepository.save(inscricao);
        }
        return inscricao;
    }

    /**
     * Completa o que faltou, cobrindo os tres casos reais:
     *
     * <ul>
     *   <li><b>nao leu QR nenhum</b> - cria entrada e saida;</li>
     *   <li><b>leu so a entrada</b> - preenche a saida, e <b>preserva a
     *       entrada real</b>, que e dado verdadeiro: ele escaneou
     *       mesmo, aquele horario aconteceu;</li>
     *   <li><b>leu os dois</b> - nao ha o que compensar, e recusar deixa
     *       claro que o admin mirou no aluno errado.</li>
     * </ul>
     *
     * <p>Completar em vez de recriar tambem e o que respeita a UNIQUE de
     * {@code checkins.inscricao_id}: so existe uma linha por inscricao.
     */
    private Checkin completarCheckin(Inscricao inscricao, Evento evento, Usuario admin, String justificativa) {
        Checkin checkin = checkinRepository.findByInscricaoId(inscricao.getId()).orElse(null);

        if (checkin == null) {
            return checkinRepository.save(Checkin.builder()
                    .inscricao(inscricao)
                    .entrada(evento.getDataHoraInicio())
                    .saida(evento.getDataHoraFim())
                    .registradoPor(admin)
                    .justificativa(justificativa)
                    .build());
        }

        if (checkin.getSaida() != null) {
            throw new ConflitoException(
                    "Este aluno ja tem check-in e check-out completos neste evento.");
        }

        // A saida nunca pode ficar antes da entrada: quem escaneou dentro
        // da tolerancia pode ter entrado depois do fim programado, e uma
        // permanencia negativa quebraria o percentual da lista.
        LocalDateTime saida = checkin.getEntrada().isAfter(evento.getDataHoraFim())
                ? checkin.getEntrada()
                : evento.getDataHoraFim();

        checkin.setSaida(saida);
        checkin.setRegistradoPor(admin);
        checkin.setJustificativa(justificativa);
        return checkinRepository.save(checkin);
    }
}

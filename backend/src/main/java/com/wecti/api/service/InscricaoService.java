package com.wecti.api.service;

import com.wecti.api.domain.Checkin;
import com.wecti.api.domain.Evento;
import com.wecti.api.domain.Inscricao;
import com.wecti.api.domain.InscricaoStatus;
import com.wecti.api.domain.Usuario;
import com.wecti.api.dto.CheckinResponse;
import com.wecti.api.dto.InscricaoResponse;
import com.wecti.api.dto.InscritoEventoResponse;
import com.wecti.api.exception.ConflitoException;
import com.wecti.api.exception.RecursoNaoEncontradoException;
import com.wecti.api.exception.RegraNegocioException;
import com.wecti.api.repository.CheckinRepository;
import com.wecti.api.repository.EventoRepository;
import com.wecti.api.repository.InscricaoRepository;
import com.wecti.api.repository.UsuarioRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InscricaoService {

    private final InscricaoRepository inscricaoRepository;
    private final EventoRepository eventoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CheckinRepository checkinRepository;
    private final PrazoInscricao prazoInscricao;

    public InscricaoService(InscricaoRepository inscricaoRepository, EventoRepository eventoRepository,
                             UsuarioRepository usuarioRepository, CheckinRepository checkinRepository,
                             PrazoInscricao prazoInscricao) {
        this.inscricaoRepository = inscricaoRepository;
        this.eventoRepository = eventoRepository;
        this.usuarioRepository = usuarioRepository;
        this.checkinRepository = checkinRepository;
        this.prazoInscricao = prazoInscricao;
    }

    /**
     * Inscreve o aluno, respeitando a capacidade do evento.
     *
     * <p>Transacional e com a linha do evento travada
     * ({@code travarParaInscricao}): contar vagas e inserir precisa ser
     * atomico, senao dois alunos contam a mesma ultima vaga ao mesmo
     * tempo e ambos entram. Com a turma clicando junto na abertura das
     * inscricoes, isso deixa de ser hipotese.
     */
    @Transactional
    public Inscricao inscrever(UUID eventoId, UUID alunoId) {
        Evento evento = eventoRepository.travarParaInscricao(eventoId.toString())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Evento nao encontrado: " + eventoId));
        Usuario aluno = usuarioRepository.findById(alunoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuario nao encontrado: " + alunoId));

        // A inscricao fecha logo apos o inicio do evento (ver
        // PrazoInscricao - ha uma folga para quem chega atrasado). Antes
        // o limite era o FIM do evento, o que deixava se inscrever num
        // evento ja encerrado e levar no-show na hora, por uma palestra
        // que nunca houve chance de assistir.
        if (!prazoInscricao.estaAberto(evento)) {
            throw new RegraNegocioException(evento.isEncerrado()
                    ? "Este evento ja terminou."
                    : "As inscricoes para este evento ja fecharam.");
        }

        var existente = inscricaoRepository.findByAlunoIdAndEventoId(alunoId, eventoId);
        if (existente.isPresent() && existente.get().getStatus() == InscricaoStatus.ATIVA) {
            throw new ConflitoException("Aluno ja possui inscricao para este evento");
        }

        exigirVagaDisponivel(evento);

        // Quem cancelou e mudou de ideia reaproveita a propria inscricao.
        // Sem isto, cancelar era irreversivel: a checagem de duplicata
        // barrava a volta, e o aluno ficava de fora de um evento que
        // ainda tem vaga.
        if (existente.isPresent()) {
            Inscricao inscricao = existente.get();
            inscricao.setStatus(InscricaoStatus.ATIVA);
            inscricao.setCanceladaEm(null);
            return inscricaoRepository.save(inscricao);
        }

        Inscricao inscricao = Inscricao.builder()
                .aluno(aluno)
                .evento(evento)
                .status(InscricaoStatus.ATIVA)
                .build();
        return inscricaoRepository.save(inscricao);
    }

    /** Vagas ocupadas agora - so inscricoes ativas. */
    public long inscritosAtivos(UUID eventoId) {
        return inscricaoRepository.countByEventoIdAndStatus(eventoId, InscricaoStatus.ATIVA);
    }

    /** Ocupacao de varios eventos de uma vez, para a lista de eventos
     *  nao fazer uma consulta por linha. */
    public Map<UUID, Long> inscritosAtivosPorEvento(Collection<UUID> eventoIds) {
        if (eventoIds.isEmpty()) {
            return Map.of();
        }
        return inscricaoRepository.contarPorEvento(eventoIds, InscricaoStatus.ATIVA).stream()
                .collect(Collectors.toMap(linha -> (UUID) linha[0], linha -> (Long) linha[1]));
    }

    private void exigirVagaDisponivel(Evento evento) {
        if (evento.getCapacidade() == null) {
            return;
        }
        long ocupadas = inscritosAtivos(evento.getId());
        if (ocupadas >= evento.getCapacidade()) {
            throw new ConflitoException(
                    "As vagas deste evento se esgotaram (" + evento.getCapacidade() + " lugares)");
        }
    }

    public void cancelar(UUID inscricaoId, UUID alunoId) {
        Inscricao inscricao = buscarEntidade(inscricaoId);
        exigirDono(inscricao, alunoId);

        if (inscricao.getStatus() == InscricaoStatus.CANCELADA) {
            throw new RegraNegocioException("Inscricao ja esta cancelada");
        }

        // Cancelamento fecha junto com a inscricao. O prazo anterior era
        // "1 dia antes", o que tornava impossivel desistir de um evento
        // marcado para o mesmo dia. Fechando os dois no mesmo instante,
        // enquanto da para entrar tambem da para sair - e quem desiste
        // devolve a vaga para outro.
        if (!prazoInscricao.estaAberto(inscricao.getEvento())) {
            throw new RegraNegocioException(
                    "O prazo para cancelar esta inscricao ja passou.");
        }

        inscricao.setStatus(InscricaoStatus.CANCELADA);
        inscricao.setCanceladaEm(LocalDateTime.now());
        inscricaoRepository.save(inscricao);
    }

    public Inscricao buscarEntidade(UUID inscricaoId) {
        return inscricaoRepository.findById(inscricaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Inscricao nao encontrada: " + inscricaoId));
    }

    public Inscricao buscarComPermissao(UUID inscricaoId, UUID alunoIdSeAluno) {
        Inscricao inscricao = buscarEntidade(inscricaoId);
        if (alunoIdSeAluno != null) {
            exigirDono(inscricao, alunoIdSeAluno);
        }
        return inscricao;
    }

    public List<Inscricao> listarPorEvento(UUID eventoId) {
        return inscricaoRepository.findByEventoId(eventoId);
    }

    /**
     * Nome e e-mail de quem esta inscrito no evento, para o admin falar
     * com a turma.
     *
     * <p><b>Sempre atualizada por construcao.</b> E uma consulta ao banco
     * a cada chamada - nao ha cache, copia materializada nem job de
     * sincronia. Nao existe estado intermediario para envelhecer: o que a
     * lista mostra e o que esta na tabela naquele instante. Uma inscricao
     * feita ha um segundo ja aparece; um cancelamento some na hora.
     *
     * <p>Confere o evento antes de consultar para separar "evento que nao
     * existe" (404) de "evento sem ninguem inscrito" (lista vazia). Sem
     * isso, um UUID errado devolveria 200 com lista vazia e o admin ficaria
     * procurando alunos que nunca estiveram ali.
     */
    @Transactional(readOnly = true)
    public List<InscritoEventoResponse> listarInscritosDoEvento(UUID eventoId) {
        if (!eventoRepository.existsById(eventoId)) {
            throw new RecursoNaoEncontradoException("Evento nao encontrado: " + eventoId);
        }
        return inscricaoRepository.findInscritosDoEvento(eventoId, InscricaoStatus.ATIVA);
    }

    public List<Inscricao> listarDoAluno(UUID alunoId, String status) {
        List<Inscricao> inscricoes = inscricaoRepository.findByAlunoId(alunoId);
        // "futuros" = inscricao ativa em evento que ainda nao terminou.
        // Antes o corte era o INICIO do evento, e a inscricao sumia de
        // "Minhas inscricoes" no instante em que a palestra comecava -
        // justamente quando o aluno precisa dela na tela para fazer o
        // check-in. As duas listas continuam complementares: o que sai
        // daqui aparece no historico.
        if ("futuros".equals(status)) {
            return inscricoes.stream()
                    .filter(i -> i.getStatus() == InscricaoStatus.ATIVA && !i.getEvento().isEncerrado())
                    .toList();
        }
        if ("historico".equals(status)) {
            return inscricoes.stream()
                    .filter(i -> i.getStatus() == InscricaoStatus.CANCELADA || i.getEvento().isEncerrado())
                    .toList();
        }
        return inscricoes;
    }

    public InscricaoResponse montarResposta(Inscricao inscricao) {
        Optional<Checkin> checkin = checkinRepository.findByInscricaoId(inscricao.getId());
        CheckinResponse checkinResponse = checkin.map(c -> CheckinResponse.de(c, inscricao.getEvento())).orElse(null);
        boolean certificadoDisponivel = checkin
                .map(c -> c.isPresencaQualificada(inscricao.getEvento().getDataHoraInicio(),
                        inscricao.getEvento().getDataHoraFim()))
                .orElse(false);
        return InscricaoResponse.de(inscricao, checkinResponse, certificadoDisponivel);
    }

    private void exigirDono(Inscricao inscricao, UUID alunoId) {
        if (!inscricao.getAluno().getId().equals(alunoId)) {
            throw new AccessDeniedException("Inscricao pertence a outro aluno");
        }
    }
}

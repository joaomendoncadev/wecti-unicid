package com.wecti.api.service;

import com.wecti.api.domain.Evento;
import com.wecti.api.dto.EventoResponse;
import com.wecti.api.dto.NovoEventoRequest;
import com.wecti.api.exception.CampoInvalidoException;
import com.wecti.api.exception.RecursoNaoEncontradoException;
import com.wecti.api.exception.RegraNegocioException;
import com.wecti.api.repository.EventoRepository;
import com.wecti.api.repository.InscricaoRepository;
import com.wecti.api.repository.PalestranteRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EventoService {

    private final EventoRepository eventoRepository;
    private final PalestranteRepository palestranteRepository;
    private final InscricaoRepository inscricaoRepository;
    private final InscricaoService inscricaoService;
    private final PrazoInscricao prazoInscricao;

    public EventoService(EventoRepository eventoRepository, PalestranteRepository palestranteRepository,
                          InscricaoRepository inscricaoRepository, InscricaoService inscricaoService,
                          PrazoInscricao prazoInscricao) {
        this.eventoRepository = eventoRepository;
        this.palestranteRepository = palestranteRepository;
        this.inscricaoRepository = inscricaoRepository;
        this.inscricaoService = inscricaoService;
        this.prazoInscricao = prazoInscricao;
    }

    /** Monta a resposta ja com o prazo de inscricao resolvido - unico
     *  ponto que traduz Evento em EventoResponse. */
    private EventoResponse responder(Evento evento, long inscritos) {
        return EventoResponse.de(evento, inscritos,
                prazoInscricao.estaAberto(evento), prazoInscricao.fechamento(evento));
    }

    /**
     * Sempre em ordem cronologica crescente - a proxima palestra primeiro.
     * Antes saia na ordem que o banco devolvia (sem ORDER BY, ou seja,
     * ordem nenhuma garantida), e a grade da semana aparecia embaralhada
     * na tela do aluno.
     *
     * <p>A ordenacao fica aqui, e nao em cada tela: as duas listagens
     * (aluno e admin) leem desta mesma rota, e ordenar no cliente daria
     * o trabalho em dobro para chegar no mesmo lugar.
     */
    public List<Evento> listar(String status) {
        List<Evento> eventos = eventoRepository.findAll(Sort.by(Sort.Direction.ASC, "dataHoraInicio"));

        if ("futuros".equals(status)) {
            return eventos.stream().filter(prazoInscricao::estaAberto).toList();
        }
        if ("encerrados".equals(status)) {
            return eventos.stream().filter(Evento::isEncerrado).toList();
        }
        // "em_cartaz": tudo que ainda nao terminou - o que o aluno ve na
        // tela de Eventos. Inclui o que esta acontecendo agora: sumir da
        // lista na hora exata em que a palestra comeca faz o aluno achar
        // que o evento foi cancelado. Depois de terminar, o evento sai
        // daqui e so aparece no filtro "encerrados".
        if ("em_cartaz".equals(status)) {
            return eventos.stream().filter(e -> !e.isEncerrado()).toList();
        }
        return eventos;
    }

    public Evento buscarPorId(UUID id) {
        return eventoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Evento nao encontrado: " + id));
    }

    /**
     * Lista os eventos ja com a ocupacao de vagas preenchida. A contagem
     * sai de uma consulta agrupada unica - contar evento a evento faria
     * uma consulta por linha da tela.
     */
    public List<EventoResponse> listarComVagas(String status) {
        List<Evento> eventos = listar(status);
        Map<UUID, Long> ocupacao = inscricaoService.inscritosAtivosPorEvento(
                eventos.stream().map(Evento::getId).toList());
        return eventos.stream()
                .map(e -> responder(e, ocupacao.getOrDefault(e.getId(), 0L)))
                .toList();
    }

    public EventoResponse detalharComVagas(UUID id) {
        Evento evento = buscarPorId(id);
        return responder(evento, inscricaoService.inscritosAtivos(id));
    }

    public EventoResponse criar(NovoEventoRequest request) {
        validarDatas(request);
        Evento evento = Evento.builder()
                .titulo(request.titulo())
                .descricao(request.descricao())
                .local(request.local())
                .dataHoraInicio(request.dataHoraInicio())
                .dataHoraFim(request.dataHoraFim())
                .pontos(request.pontos())
                .capacidade(request.capacidade())
                .palestrantes(buscarPalestrantes(request.palestranteIds()))
                .build();
        return responder(eventoRepository.save(evento), 0L);
    }

    public EventoResponse atualizar(UUID id, NovoEventoRequest request) {
        validarDatas(request);
        Evento evento = buscarPorId(id);
        long inscritos = inscricaoService.inscritosAtivos(id);
        validarCapacidade(request.capacidade(), inscritos);

        evento.setTitulo(request.titulo());
        evento.setDescricao(request.descricao());
        evento.setLocal(request.local());
        evento.setDataHoraInicio(request.dataHoraInicio());
        evento.setDataHoraFim(request.dataHoraFim());
        evento.setPontos(request.pontos());
        evento.setCapacidade(request.capacidade());
        evento.setPalestrantes(buscarPalestrantes(request.palestranteIds()));
        return responder(eventoRepository.save(evento), inscritos);
    }

    /**
     * Reduzir a capacidade abaixo de quem ja esta inscrito deixaria o
     * evento num estado impossivel de resolver pelo sistema: o admin
     * teria que escolher a mao quem perde a vaga, e nao ha tela para
     * isso. Melhor recusar e deixar claro quantos ja entraram.
     */
    private void validarCapacidade(Integer capacidade, long inscritos) {
        if (capacidade != null && capacidade < inscritos) {
            throw new RegraNegocioException(
                    "Este evento ja tem " + inscritos + " inscrito(s) - a capacidade nao pode ser menor que isso.");
        }
    }

    public void cancelar(UUID id) {
        Evento evento = buscarPorId(id);
        if (!inscricaoRepository.findByEventoId(id).isEmpty()) {
            throw new RegraNegocioException("Nao e possivel cancelar um evento que ja possui inscricoes");
        }
        eventoRepository.delete(evento);
    }

    private void validarDatas(NovoEventoRequest request) {
        if (!request.dataHoraFim().isAfter(request.dataHoraInicio())) {
            throw new CampoInvalidoException("dataHoraFim", "A data/hora de termino deve ser depois da data/hora de inicio");
        }
    }

    private Set<com.wecti.api.domain.Palestrante> buscarPalestrantes(List<UUID> palestranteIds) {
        if (palestranteIds == null || palestranteIds.isEmpty()) {
            return new HashSet<>();
        }
        // IDs unicos antes de comparar tamanho - senao uma lista com ID
        // repetido (ex.: [x, x]) disparava falso "nao existe", ja que
        // findAllById devolve so uma linha por ID unico.
        Set<UUID> idsUnicos = new HashSet<>(palestranteIds);
        List<com.wecti.api.domain.Palestrante> encontrados = palestranteRepository.findAllById(idsUnicos);
        if (encontrados.size() != idsUnicos.size()) {
            throw new RecursoNaoEncontradoException("Um ou mais palestrantes informados nao existem");
        }
        return new HashSet<>(encontrados);
    }
}

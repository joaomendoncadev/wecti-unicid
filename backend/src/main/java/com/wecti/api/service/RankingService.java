package com.wecti.api.service;

import com.wecti.api.domain.Checkin;
import com.wecti.api.domain.Evento;
import com.wecti.api.domain.Inscricao;
import com.wecti.api.domain.Usuario;
import com.wecti.api.dto.RankingItemResponse;
import com.wecti.api.dto.RankingResponse;
import com.wecti.api.repository.CheckinRepository;
import com.wecti.api.repository.EventoRepository;
import com.wecti.api.repository.InscricaoRepository;
import com.wecti.api.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.text.Collator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Classificacao dos alunos por pontos no WECTI.
 *
 * <p><b>Por que nao reutiliza PontuacaoService.</b> A regra e a mesma
 * (esta em {@link RegraPontuacao}, usada pelos dois), mas o
 * acesso a dados nao pode ser: chamar o calculo individual para cada
 * aluno faria duas consultas por evento por aluno - com 400 alunos e 10
 * eventos, 8 mil consultas para montar uma tela. Aqui tudo do periodo e
 * carregado em quatro consultas e distribuido em memoria.
 *
 * <p><b>Sem recorte por semestre.</b> O conceito de "periodo" foi
 * removido do sistema (ver V7__remove_periodo.sql): nunca foi validado
 * com o professor e fazia o ranking parar de carregar assim que o
 * semestre cadastrado terminava.
 *
 * <p><b>Empates.</b> Quem tem o mesmo total divide a mesma posicao, e a
 * posicao seguinte pula (1, 2, 2, 4). Inventar desempate por criterio
 * oculto seria pior: o aluno veria dois totais iguais em posicoes
 * diferentes sem entender por que.
 */
@Service
public class RankingService {

    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final CheckinRepository checkinRepository;
    private final PontuacaoExtraService pontuacaoExtraService;
    private final UsuarioRepository usuarioRepository;
    private final RegraPontuacao regraPontuacao;

    public RankingService(EventoRepository eventoRepository, InscricaoRepository inscricaoRepository,
                           CheckinRepository checkinRepository, PontuacaoExtraService pontuacaoExtraService,
                           UsuarioRepository usuarioRepository, RegraPontuacao regraPontuacao) {
        this.eventoRepository = eventoRepository;
        this.inscricaoRepository = inscricaoRepository;
        this.checkinRepository = checkinRepository;
        this.pontuacaoExtraService = pontuacaoExtraService;
        this.usuarioRepository = usuarioRepository;
        this.regraPontuacao = regraPontuacao;
    }

    /**
     * @param incluirRgm o RGM so vai para a tela do admin, que precisa
     *                   dele para nao lancar pontos no aluno errado
     *                   (homonimos existem). Na tela do aluno o ranking
     *                   mostra nome e curso, e mais nada.
     */
    public RankingResponse montar(boolean incluirRgm) {
        Map<UUID, Evento> eventos = eventoRepository.findAll().stream()
                .collect(Collectors.toMap(Evento::getId, Function.identity()));
        List<Inscricao> inscricoes = inscricaoRepository.findTodasParaRanking();
        Map<UUID, Checkin> checkinsPorInscricao = checkinRepository.findTodosParaRanking().stream()
                .collect(Collectors.toMap(c -> c.getInscricao().getId(), Function.identity()));
        Map<UUID, Integer> extras = pontuacaoExtraService.totaisPorAluno();

        LocalDateTime agora = LocalDateTime.now();
        Map<UUID, Usuario> alunos = new HashMap<>();
        Map<UUID, RegraPontuacao.Totalizador> pontosEventos = new HashMap<>();
        Map<UUID, Integer> eventosConcluidos = new HashMap<>();

        for (Inscricao inscricao : inscricoes) {
            Evento evento = eventos.get(inscricao.getEvento().getId());
            if (evento == null) {
                continue;
            }
            Usuario aluno = inscricao.getAluno();
            alunos.putIfAbsent(aluno.getId(), aluno);
            pontosEventos.computeIfAbsent(aluno.getId(), id -> regraPontuacao.totalizador());
            eventosConcluidos.putIfAbsent(aluno.getId(), 0);

            var resultado = regraPontuacao.avaliar(
                    evento, inscricao, checkinsPorInscricao.get(inscricao.getId()), agora);
            if (resultado == null) {
                continue;
            }
            pontosEventos.get(aluno.getId()).somar(resultado);
            if (RegraPontuacao.CONCLUIDO.equals(resultado.status()) && resultado.pontos() > 0) {
                eventosConcluidos.merge(aluno.getId(), 1, Integer::sum);
            }
        }

        // Aluno que so recebeu pontos de gincana, sem inscricao nenhuma,
        // tambem disputa - senao sumiria de um ranking em que tem pontos.
        // Nao esta em "inscricoes", entao precisa ser buscado.
        List<UUID> semInscricao = extras.keySet().stream()
                .filter(alunoId -> !alunos.containsKey(alunoId))
                .toList();
        if (!semInscricao.isEmpty()) {
            usuarioRepository.findAllById(semInscricao).forEach(a -> alunos.put(a.getId(), a));
        }

        // Nomes proprios em portugues: sem Collator, "Ávila" cairia
        // depois de "Zeni" no desempate alfabetico.
        Collator collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"));

        List<Parcial> parciais = alunos.values().stream()
                .map(aluno -> new Parcial(
                        aluno,
                        pontosEventos.containsKey(aluno.getId())
                                ? pontosEventos.get(aluno.getId()).total()
                                : 0,
                        extras.getOrDefault(aluno.getId(), 0),
                        eventosConcluidos.getOrDefault(aluno.getId(), 0)))
                .sorted(Comparator.comparingInt(Parcial::total).reversed()
                        .thenComparing(p -> p.aluno().getNome(), collator))
                .toList();

        List<RankingItemResponse> itens = new ArrayList<>(parciais.size());
        for (int i = 0; i < parciais.size(); i++) {
            Parcial parcial = parciais.get(i);
            // Mesmo total = mesma posicao; a proxima posicao pula os
            // empatados (1, 2, 2, 4).
            int posicao = (i > 0 && parciais.get(i - 1).total() == parcial.total())
                    ? itens.get(i - 1).posicao()
                    : i + 1;
            itens.add(new RankingItemResponse(
                    posicao,
                    parcial.aluno().getId(),
                    parcial.aluno().getNome(),
                    incluirRgm ? parcial.aluno().getRgm() : null,
                    parcial.aluno().getCurso(),
                    parcial.pontosEventos(),
                    parcial.pontosExtras(),
                    parcial.total(),
                    parcial.eventosConcluidos()));
        }

        return new RankingResponse(itens);
    }

    private record Parcial(Usuario aluno, int pontosEventos, int pontosExtras, int eventosConcluidos) {
        int total() {
            return pontosEventos + pontosExtras;
        }
    }
}

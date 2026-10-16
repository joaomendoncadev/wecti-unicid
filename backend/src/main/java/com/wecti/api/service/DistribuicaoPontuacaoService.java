package com.wecti.api.service;

import com.wecti.api.domain.Disciplina;
import com.wecti.api.domain.DistribuicaoPontuacao;
import com.wecti.api.domain.Perfil;
import com.wecti.api.domain.Usuario;
import com.wecti.api.dto.DistribuicaoDoAlunoResponse;
import com.wecti.api.dto.DistribuicaoResponse;
import com.wecti.api.dto.ItemDistribuicaoRequest;
import com.wecti.api.dto.ItemDistribuicaoResponse;
import com.wecti.api.exception.CampoInvalidoException;
import com.wecti.api.exception.RecursoNaoEncontradoException;
import com.wecti.api.exception.RegraNegocioException;
import com.wecti.api.repository.DisciplinaRepository;
import com.wecti.api.repository.DistribuicaoPontuacaoRepository;
import com.wecti.api.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * O aluno converte a pontuacao que ganhou no WECTI em nota, escolhendo em
 * quais turmas ela entra.
 *
 * <p>Pedido do professor em outubro de 2026, depois do evento: quem
 * juntou pontos nas palestras troca por decimo de nota nas materias que
 * quiser. 1000 pontos viram 1,0 de nota, com teto de 2,0 - ver
 * {@link RegraDistribuicao}.
 *
 * <p><b>Duas regras moldam tudo aqui:</b>
 *
 * <ul>
 *   <li><b>Nao pode passar do que tem.</b> O limite e o total do aluno
 *       aparado em 2000. Quem tem 2300 distribui 2000; quem tem 1300
 *       distribui 1300, repartidos como quiser.</li>
 *   <li><b>Tem de usar tudo.</b> Sobrar ponto e desperdicio de nota que
 *       ele ja conquistou. Isso NAO bloqueia salvar - ver
 *       {@link #salvar} - mas e o que {@code completa} reporta, e e o
 *       que libera o comprovante.</li>
 * </ul>
 */
@Service
public class DistribuicaoPontuacaoService {

    private final DistribuicaoPontuacaoRepository repository;
    private final DisciplinaRepository disciplinaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PontuacaoService pontuacaoService;
    private final RegraDistribuicao regra;

    public DistribuicaoPontuacaoService(DistribuicaoPontuacaoRepository repository,
                                         DisciplinaRepository disciplinaRepository,
                                         UsuarioRepository usuarioRepository,
                                         PontuacaoService pontuacaoService,
                                         RegraDistribuicao regra) {
        this.repository = repository;
        this.disciplinaRepository = disciplinaRepository;
        this.usuarioRepository = usuarioRepository;
        this.pontuacaoService = pontuacaoService;
        this.regra = regra;
    }

    /** Quanto este aluno pode distribuir no total. */
    public int limiteDe(UUID alunoId) {
        return regra.limiteDisponivel(pontuacaoService.calcular(alunoId).pontosTotal());
    }

    public List<DistribuicaoPontuacao> listarDoAluno(UUID alunoId) {
        return repository.findByAlunoId(alunoId);
    }

    /**
     * A distribuicao do aluno com a conta ja fechada (limite, distribuido,
     * restante). A tela nao refaz essa conta: o teto de 2000 e a conversao
     * em nota ficam so aqui.
     */
    public DistribuicaoResponse responderDoAluno(UUID alunoId) {
        int limite = limiteDe(alunoId);
        List<DistribuicaoPontuacao> linhas = repository.findByAlunoId(alunoId);
        int distribuido = linhas.stream().mapToInt(DistribuicaoPontuacao::getPontos).sum();

        return new DistribuicaoResponse(
                limite,
                distribuido,
                Math.max(0, limite - distribuido),
                distribuido == limite && limite > 0,
                regra.notaDe(distribuido),
                regra.pontosPorNota(),
                linhas.stream()
                        .sorted(Comparator.comparing(d -> d.getDisciplina().getNome()))
                        .map(d -> ItemDistribuicaoResponse.de(d, regra.notaDe(d.getPontos())))
                        .toList());
    }

    /**
     * Consulta do admin: todos os alunos que distribuiram, agrupados por
     * aluno.
     *
     * <p>Agrupado, e nao uma linha solta por turma, porque a pergunta e
     * "o que o fulano distribuiu?" - e conferir se a soma dele fecha
     * exige ver as linhas juntas.
     */
    public List<DistribuicaoDoAlunoResponse> responderTodas() {
        return repository.findTodasParaConsulta().stream()
                .collect(Collectors.groupingBy(DistribuicaoPontuacao::getAluno,
                        LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entrada -> {
                    Usuario aluno = entrada.getKey();
                    List<DistribuicaoPontuacao> linhas = entrada.getValue();
                    int total = linhas.stream().mapToInt(DistribuicaoPontuacao::getPontos).sum();
                    return new DistribuicaoDoAlunoResponse(
                            aluno.getNome(),
                            aluno.getRgm(),
                            total,
                            regra.notaDe(total),
                            linhas.stream()
                                    .map(d -> ItemDistribuicaoResponse.de(d, regra.notaDe(d.getPontos())))
                                    .toList());
                })
                .toList();
    }

    /**
     * Substitui a distribuicao inteira do aluno pelo que veio na lista.
     *
     * <p>Substituir, e nao acumular: a tela manda o estado completo da
     * distribuicao a cada salvamento, entao uma turma que sumiu da lista
     * foi uma turma que o aluno zerou. Com soma incremental, tirar pontos
     * de uma materia exigiria um "delete" separado e a tela teria de
     * descobrir sozinha o que mudou - mais chamadas e mais chance de
     * ficar inconsistente no meio.
     *
     * <p><b>Salvar parcial e permitido de proposito.</b> A regra e usar
     * tudo, mas exigir o total exato para gravar obrigaria o aluno a
     * decidir as 131 turmas de uma sentada so, sem poder voltar depois.
     * Enquanto a edicao esta aberta, o que falta aparece como
     * {@code restante} na tela, e o comprovante so sai quando fecha em
     * zero.
     */
    @Transactional
    public List<DistribuicaoPontuacao> salvar(UUID alunoId, List<ItemDistribuicaoRequest> itens) {
        Usuario aluno = usuarioRepository.findById(alunoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aluno nao encontrado"));
        if (aluno.getPerfil() != Perfil.ALUNO) {
            throw new CampoInvalidoException("alunoId", "So aluno distribui pontuacao em nota");
        }

        List<ItemDistribuicaoRequest> validos = itens.stream()
                .filter(i -> i.pontos() != null && i.pontos() > 0)
                .toList();
        exigirSemTurmaRepetida(validos);

        int total = validos.stream().mapToInt(ItemDistribuicaoRequest::pontos).sum();
        int limite = limiteDe(alunoId);
        if (total > limite) {
            throw new RegraNegocioException(
                    "Voce esta distribuindo " + total + " pontos, mas so tem " + limite
                            + " disponiveis. Reduza " + (total - limite) + " pontos.");
        }

        Map<UUID, Disciplina> turmas = carregarTurmas(validos);

        // Troca completa: apaga o que havia e grava o estado novo. Mais
        // simples do que casar linha a linha, e a distribuicao de um
        // aluno tem dezenas de linhas, nao milhares.
        repository.deleteByAlunoId(alunoId);
        repository.flush();

        List<DistribuicaoPontuacao> novas = validos.stream()
                .map(i -> DistribuicaoPontuacao.builder()
                        .aluno(aluno)
                        .disciplina(turmas.get(i.disciplinaId()))
                        .pontos(i.pontos())
                        .build())
                .toList();
        return repository.saveAll(novas);
    }

    /**
     * A mesma turma duas vezes no corpo da requisicao seria ambigua - vale
     * a primeira ou a ultima? - e a UNIQUE (aluno, disciplina) estouraria
     * na gravacao como erro 500. Melhor recusar com mensagem clara.
     */
    private void exigirSemTurmaRepetida(List<ItemDistribuicaoRequest> itens) {
        Set<UUID> vistas = new HashSet<>();
        for (ItemDistribuicaoRequest item : itens) {
            if (!vistas.add(item.disciplinaId())) {
                throw new CampoInvalidoException("itens",
                        "A mesma turma aparece mais de uma vez na distribuicao");
            }
        }
    }

    /** Carrega as turmas de uma vez e confere que todas existem - uma
     *  consulta, em vez de uma por item. */
    private Map<UUID, Disciplina> carregarTurmas(List<ItemDistribuicaoRequest> itens) {
        Set<UUID> ids = itens.stream().map(ItemDistribuicaoRequest::disciplinaId)
                .collect(Collectors.toSet());
        Map<UUID, Disciplina> encontradas = disciplinaRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Disciplina::getId, Function.identity()));
        if (encontradas.size() != ids.size()) {
            throw new RecursoNaoEncontradoException("Uma ou mais turmas informadas nao existem");
        }
        return encontradas;
    }
}

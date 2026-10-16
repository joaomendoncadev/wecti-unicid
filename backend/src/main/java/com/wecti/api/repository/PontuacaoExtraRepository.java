package com.wecti.api.repository;

import com.wecti.api.domain.PontuacaoExtra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PontuacaoExtraRepository extends JpaRepository<PontuacaoExtra, UUID> {

    /** Lancamentos de um aluno - tela de pontuacao do aluno e historico
     *  que o admin ve antes de lancar mais pontos. */
    List<PontuacaoExtra> findByAlunoIdOrderByCriadoEmDesc(UUID alunoId);

    /** Ao excluir um aluno: os pontos de gincana dele saem junto. */
    void deleteByAlunoId(UUID alunoId);

    /**
     * Este usuario ja lancou pontos em alguem? Se sim, nao da para
     * apaga-lo: {@code pontuacoes_extras.criado_por_id} e NOT NULL, e os
     * lancamentos pertencem aos alunos premiados, nao a quem lancou -
     * apagar junto tiraria pontos de terceiros. Ver UsuarioService.excluir.
     */
    boolean existsByCriadoPorId(UUID usuarioId);

    /** Todos os lancamentos de uma vez, com o aluno carregado - o ranking
     *  soma os extras da turma inteira e nao pode consultar aluno a aluno. */
    @Query("select e from PontuacaoExtra e join fetch e.aluno")
    List<PontuacaoExtra> findTodosParaRanking();
}

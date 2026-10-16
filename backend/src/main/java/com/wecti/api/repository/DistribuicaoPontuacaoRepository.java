package com.wecti.api.repository;

import com.wecti.api.domain.DistribuicaoPontuacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DistribuicaoPontuacaoRepository extends JpaRepository<DistribuicaoPontuacao, UUID> {

    List<DistribuicaoPontuacao> findByAlunoId(UUID alunoId);

    /** Ao excluir um aluno: a distribuicao dele sai junto (ver
     *  UsuarioService.excluir). Sem isto a FK aluno_id barra a exclusao. */
    void deleteByAlunoId(UUID alunoId);

    /**
     * Consulta do admin: a turma inteira de uma vez, com aluno e
     * disciplina ja carregados.
     *
     * <p>Sem o fetch seriam duas consultas por linha para montar nome,
     * RGM e professor - com algumas centenas de alunos distribuindo em
     * varias turmas, a tela nao abriria.
     */
    @Query("""
            select d from DistribuicaoPontuacao d
              join fetch d.aluno a
              join fetch d.disciplina t
             order by a.nome asc, t.codigo asc
            """)
    List<DistribuicaoPontuacao> findTodasParaConsulta();
}

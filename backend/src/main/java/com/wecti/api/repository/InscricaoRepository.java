package com.wecti.api.repository;

import com.wecti.api.domain.Inscricao;
import com.wecti.api.domain.InscricaoStatus;
import com.wecti.api.dto.InscritoEventoResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InscricaoRepository extends JpaRepository<Inscricao, UUID> {
    List<Inscricao> findByAlunoId(UUID alunoId);
    List<Inscricao> findByAlunoIdAndStatus(UUID alunoId, InscricaoStatus status);
    List<Inscricao> findByEventoId(UUID eventoId);
    Optional<Inscricao> findByAlunoIdAndEventoId(UUID alunoId, UUID eventoId);
    boolean existsByAlunoIdAndEventoId(UUID alunoId, UUID eventoId);
    boolean existsByAlunoId(UUID alunoId);

    /** Vagas ocupadas de um evento. So ATIVA conta - quem cancelou
     *  devolveu a vaga. */
    long countByEventoIdAndStatus(UUID eventoId, InscricaoStatus status);

    /**
     * Ocupacao de varios eventos numa consulta so. A lista de eventos
     * mostra "X de Y vagas" em cada linha; contar um a um faria uma
     * consulta por evento na tela.
     */
    @Query("""
            select i.evento.id, count(i)
              from Inscricao i
             where i.status = :status
               and i.evento.id in :eventoIds
             group by i.evento.id
            """)
    List<Object[]> contarPorEvento(@Param("eventoIds") Collection<UUID> eventoIds,
                                    @Param("status") InscricaoStatus status);

    /**
     * Alunos atualmente inscritos num evento, para a lista de contato do
     * admin (GET /eventos/{id}/inscritos).
     *
     * <p>So ATIVA: quem cancelou devolveu a vaga e nao faz mais parte da
     * turma - nao deve receber o e-mail do evento.
     *
     * <p>Projeta direto no DTO em vez de carregar as entidades, por dois
     * motivos. O primeiro e que carregar o {@code Usuario} inteiro traz
     * junto o <b>hash da senha</b> de cada aluno - nao ha razao para esse
     * campo sair do banco numa tela que mostra nome e e-mail. O segundo e
     * que a entidade {@code Inscricao} tem {@code @ManyToOne} para Evento,
     * que o Hibernate carrega junto (com os palestrantes atrelados) so
     * para ser descartado aqui.
     *
     * <p>Uma consulta, com os campos exatos da resposta, seja a turma de 3
     * ou de 300. Ordenado por nome no banco, porque e assim que a lista e
     * lida.
     */
    @Query("""
            select new com.wecti.api.dto.InscritoEventoResponse(
                     a.id, a.nome, a.email, a.rgm, i.criadaEm)
              from Inscricao i
              join i.aluno a
             where i.evento.id = :eventoId
               and i.status = :status
             order by a.nome
            """)
    List<InscritoEventoResponse> findInscritosDoEvento(@Param("eventoId") UUID eventoId,
                                                        @Param("status") InscricaoStatus status);

    /** Todas as inscricoes, com aluno e evento ja carregados - o ranking
     *  precisa da turma inteira de uma vez, e sem o fetch faria uma
     *  consulta por linha para montar cada nome. */
    @Query("select i from Inscricao i join fetch i.aluno join fetch i.evento")
    List<Inscricao> findTodasParaRanking();
}

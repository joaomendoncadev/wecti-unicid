package com.wecti.api.repository;

import com.wecti.api.domain.Checkin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckinRepository extends JpaRepository<Checkin, UUID> {
    Optional<Checkin> findByInscricaoId(UUID inscricaoId);

    /** Ao excluir um aluno: os check-ins saem antes das inscricoes, que
     *  sao a ponta que eles referenciam. */
    void deleteByInscricaoIdIn(Collection<UUID> inscricaoIds);

    /** Este usuario ja lancou presenca a mao em alguem? Se sim, apaga-lo
     *  deixaria esses check-ins sem autor - ver UsuarioService.excluir. */
    boolean existsByRegistradoPorId(UUID usuarioId);

    /** Relatório de presença (admin/professor) - todo aluno que já fez
     *  check-in nesse evento, mais cedo primeiro. */
    List<Checkin> findByInscricao_Evento_IdOrderByEntradaAsc(UUID eventoId);

    /** Todos os check-ins, numa consulta só - o ranking precisa da turma
     *  inteira e não pode buscar inscrição a inscrição. */
    @Query("select c from Checkin c join fetch c.inscricao")
    List<Checkin> findTodosParaRanking();
}

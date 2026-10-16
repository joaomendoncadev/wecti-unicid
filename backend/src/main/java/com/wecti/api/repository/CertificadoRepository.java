package com.wecti.api.repository;

import com.wecti.api.domain.Certificado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface CertificadoRepository extends JpaRepository<Certificado, UUID> {
    Optional<Certificado> findByInscricaoId(UUID inscricaoId);

    /**
     * Ao excluir um aluno: os certificados saem primeiro de todos, por
     * serem a ponta mais externa da cadeia.
     *
     * <p>Isso invalida o codigo impresso: um PDF ja baixado deixa de
     * validar em GET /validar/{codigo}. E irreversivel e esperado - o
     * certificado pertencia a conta que esta sendo apagada.
     */
    void deleteByInscricaoIdIn(Collection<UUID> inscricaoIds);

    /** Usado pela pagina publica de validacao (GET /validar/{codigo}). */
    Optional<Certificado> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);
}

package com.wecti.api.repository;

import com.wecti.api.domain.Disciplina;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

/**
 * Catalogo de turmas - so leitura. Entra por migration (V9) e nao tem
 * tela de cadastro; planilha nova vira migration nova.
 *
 * <p>JpaSpecificationExecutor: a tela do aluno filtra por turno e por
 * texto livre (materia, codigo ou professor) combinados, do mesmo jeito
 * que a tela de Usuarios - ver DisciplinaService.listar.
 */
public interface DisciplinaRepository extends JpaRepository<Disciplina, UUID>,
        JpaSpecificationExecutor<Disciplina> {
}

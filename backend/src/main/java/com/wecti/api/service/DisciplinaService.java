package com.wecti.api.service;

import com.wecti.api.domain.Disciplina;
import com.wecti.api.domain.TurnoDisciplina;
import com.wecti.api.repository.DisciplinaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Catalogo de turmas que o aluno ve ao distribuir a pontuacao.
 *
 * <p>Sem paginacao, de proposito: sao 131 turmas, e o aluno precisa
 * procurar a dele percorrendo a lista. Paginar obrigaria a trocar de
 * pagina no meio da distribuicao e perder o que ja tinha digitado nas
 * outras - o filtro resolve melhor o mesmo problema.
 */
@Service
public class DisciplinaService {

    private final DisciplinaRepository repository;

    public DisciplinaService(DisciplinaRepository repository) {
        this.repository = repository;
    }

    /**
     * @param turno filtra manha/noite, ou {@code null} para os dois
     * @param busca texto livre em nome da materia, codigo ou professor -
     *              o aluno nem sempre lembra o nome exato da disciplina,
     *              mas lembra do professor
     */
    public List<Disciplina> listar(TurnoDisciplina turno, String busca) {
        Specification<Disciplina> spec = (root, query, cb) -> cb.conjunction();

        if (turno != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("turno"), turno));
        }
        if (busca != null && !busca.isBlank()) {
            String termo = "%" + busca.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nome")), termo),
                    cb.like(cb.lower(root.get("codigo")), termo),
                    cb.like(cb.lower(root.get("professor")), termo)));
        }

        // Ordem fixa por materia e depois turno/dia: a lista precisa cair
        // sempre no mesmo lugar entre um filtro e outro, senao o aluno
        // perde a linha que estava preenchendo.
        return repository.findAll(spec, Sort.by("nome").and(Sort.by("turno", "diaSemana", "professor")));
    }
}

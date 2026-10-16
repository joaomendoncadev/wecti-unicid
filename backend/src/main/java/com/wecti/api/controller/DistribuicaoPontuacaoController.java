package com.wecti.api.controller;

import com.wecti.api.domain.TurnoDisciplina;
import com.wecti.api.dto.DisciplinaResponse;
import com.wecti.api.dto.DistribuicaoDoAlunoResponse;
import com.wecti.api.dto.DistribuicaoResponse;
import com.wecti.api.dto.SalvarDistribuicaoRequest;
import com.wecti.api.security.AuthenticatedUser;
import com.wecti.api.service.DisciplinaService;
import com.wecti.api.service.DistribuicaoPontuacaoService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Conversao da pontuacao do WECTI em nota: o aluno escolhe em quais
 * turmas os pontos dele entram (ver DistribuicaoPontuacaoService).
 */
@RestController
public class DistribuicaoPontuacaoController {

    private final DisciplinaService disciplinaService;
    private final DistribuicaoPontuacaoService distribuicaoService;

    public DistribuicaoPontuacaoController(DisciplinaService disciplinaService,
                                            DistribuicaoPontuacaoService distribuicaoService) {
        this.disciplinaService = disciplinaService;
        this.distribuicaoService = distribuicaoService;
    }

    /**
     * Catalogo de turmas, com filtro de turno e busca livre. Sem
     * paginacao: sao 131 turmas e o aluno percorre a lista escolhendo as
     * dele - trocar de pagina no meio perderia o que ja digitou.
     */
    @GetMapping("/disciplinas")
    public List<DisciplinaResponse> listarDisciplinas(
            @RequestParam(required = false) TurnoDisciplina turno,
            @RequestParam(required = false) String busca) {
        return disciplinaService.listar(turno, busca).stream()
                .map(DisciplinaResponse::de)
                .toList();
    }

    /** O que o aluno ja distribuiu, com limite, restante e nota. */
    @GetMapping("/me/distribuicao")
    public DistribuicaoResponse minhaDistribuicao(@AuthenticationPrincipal AuthenticatedUser autenticado) {
        return distribuicaoService.responderDoAluno(autenticado.id());
    }

    /**
     * Substitui a distribuicao inteira pelo que veio no corpo. O id do
     * aluno sai do token, nunca do corpo - senao um aluno distribuiria a
     * pontuacao de outro.
     */
    @PutMapping("/me/distribuicao")
    public DistribuicaoResponse salvarMinhaDistribuicao(
            @Valid @RequestBody SalvarDistribuicaoRequest request,
            @AuthenticationPrincipal AuthenticatedUser autenticado) {
        distribuicaoService.salvar(autenticado.id(), request.itens());
        return distribuicaoService.responderDoAluno(autenticado.id());
    }

    /** Consulta do admin: como cada aluno repartiu a pontuacao. */
    @GetMapping("/distribuicoes")
    public List<DistribuicaoDoAlunoResponse> listarTodas() {
        return distribuicaoService.responderTodas();
    }
}

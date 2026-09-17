package com.wecti.api.controller;

import com.wecti.api.dto.RankingResponse;
import com.wecti.api.service.RankingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Classificação por pontos — tela do admin.
 *
 * <p>Até setembro de 2026 o aluno também via esta rota, com uma versão
 * reduzida (sem o RGM dos colegas). O professor pediu para tirar: o
 * aluno acompanha a própria pontuação em {@code /me/pontuacao} e não a
 * dos outros. Quem barra é o {@code SecurityConfig}, não o menu do
 * frontend — esconder o link não impediria ninguém de chamar a API.
 *
 * <p>Como só o admin chega aqui, o RGM vai sempre incluído: ele precisa
 * do identificador para não lançar pontos de gincana no aluno errado
 * (homônimos existem).
 */
@RestController
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping("/ranking")
    public RankingResponse ranking() {
        return rankingService.montar(true);
    }
}

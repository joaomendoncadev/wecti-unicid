package com.wecti.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converte pontuacao do WECTI em nota, e diz quanto o aluno pode
 * distribuir. Regra unica, num lugar so.
 *
 * <p>Existe pelo mesmo motivo de {@link RegraPontuacao}: a conta aparece
 * na tela do aluno, no comprovante que ele imprime e na consulta do
 * admin. Tres lugares mostrando numeros diferentes para a mesma nota
 * derrubaria a confianca no fim do semestre, que e justamente quando
 * ninguem tem tempo de investigar.
 *
 * <p><b>A conversao</b> e {@code app.distribuicao.pontos-por-nota}
 * (padrao 1000) pontos para cada 1,0 de nota - 2000 pontos viram 2,0;
 * 1500 viram 1,5; 300 viram 0,3.
 *
 * <p><b>O teto</b> e {@code app.distribuicao.limite-pontos} (padrao
 * 2000): quem passou disso distribui so 2000, porque 2,0 e a nota maxima.
 *
 * <p>O teto da distribuicao e configuracao PROPRIA, separada de
 * {@code app.pontuacao.limite-eventos}, embora os dois valham 2000 hoje.
 * Sao coisas diferentes: um limita o que da para GANHAR em palestras, o
 * outro o que da para CONVERTER em nota. Se o professor um dia soltar o
 * teto de ganho, o teto da nota provavelmente continua 2,0 - com um
 * parametro so, mexer em um mexeria no outro sem querer.
 */
@Service
public class RegraDistribuicao {

    private final int pontosPorNota;
    private final int limitePontos;

    public RegraDistribuicao(
            @Value("${app.distribuicao.pontos-por-nota}") int pontosPorNota,
            @Value("${app.distribuicao.limite-pontos}") int limitePontos) {
        this.pontosPorNota = pontosPorNota;
        this.limitePontos = limitePontos;
    }

    public int limitePontos() {
        return limitePontos;
    }

    public int pontosPorNota() {
        return pontosPorNota;
    }

    /**
     * Quanto este aluno pode distribuir: o que ele tem, aparado pelo teto.
     *
     * @param pontuacaoTotal o total que o aluno ve na tela de Pontuacao
     *                       (eventos ja aparados pelo teto de ganho, mais
     *                       gincana)
     */
    public int limiteDisponivel(int pontuacaoTotal) {
        return Math.min(Math.max(pontuacaoTotal, 0), limitePontos);
    }

    /**
     * Pontos em nota.
     *
     * <p>Tres casas decimais, sem arredondar para menos casas: com 1000
     * pontos por nota, qualquer valor de pontos cabe exato em tres casas
     * (137 pontos = 0,137). Arredondar para duas faria a soma das linhas
     * nao bater com o total - e o aluno conferindo o comprovante acharia,
     * com razao, que o sistema errou.
     */
    public BigDecimal notaDe(int pontos) {
        return BigDecimal.valueOf(pontos)
                .divide(BigDecimal.valueOf(pontosPorNota), 3, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }
}

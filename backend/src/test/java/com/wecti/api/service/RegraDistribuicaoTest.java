package com.wecti.api.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conversao de pontuacao em nota e teto do que da para converter.
 *
 * <p>Os tres primeiros casos sao os numeros que o professor escreveu ao
 * pedir a funcionalidade (outubro de 2026): 2000 = 2,0; 1500 = 1,5;
 * 300 = 0,3. Se a conversao mudar sem querer, quebra aqui - e nao na
 * entrega das notas no fim do semestre.
 */
class RegraDistribuicaoTest {

    /** Mesmos valores do application.yml. */
    private final RegraDistribuicao regra = new RegraDistribuicao(1000, 2000);

    @ParameterizedTest(name = "{0} pontos = nota {1}")
    @CsvSource({
            "2000, 2",
            "1500, 1.5",
            "300, 0.3",
            "0, 0",
            "1300, 1.3",
            "50, 0.05",
    })
    @DisplayName("a conversao e a que o professor pediu")
    void conversao(int pontos, String notaEsperada) {
        assertThat(regra.notaDe(pontos))
                .isEqualByComparingTo(new BigDecimal(notaEsperada));
    }

    /**
     * Tres casas sem arredondar para menos: com valores quebrados
     * (gincana pode lancar qualquer numero), arredondar para duas casas
     * faria a soma das linhas nao bater com o total - e o aluno
     * conferindo o comprovante acharia, com razao, que o sistema errou.
     */
    @Test
    @DisplayName("ponto quebrado converte exato, sem perder casa")
    void pontoQuebrado() {
        assertThat(regra.notaDe(137)).isEqualByComparingTo(new BigDecimal("0.137"));
        assertThat(regra.notaDe(1).add(regra.notaDe(1)).add(regra.notaDe(1)))
                .as("somar as linhas tem de dar o mesmo que converter o total")
                .isEqualByComparingTo(regra.notaDe(3));
    }

    @Test
    @DisplayName("quem tem menos que o teto distribui tudo que tem")
    void abaixoDoTeto() {
        assertThat(regra.limiteDisponivel(1300)).isEqualTo(1300);
        assertThat(regra.limiteDisponivel(0)).isZero();
    }

    /** "Alunos que constam com pontuacao > 2000 serao limitados a usar
     *  apenas os 2000 pontos. Este e o limite." */
    @Test
    @DisplayName("quem passou de 2000 distribui so 2000")
    void acimaDoTeto() {
        assertThat(regra.limiteDisponivel(2000)).isEqualTo(2000);
        assertThat(regra.limiteDisponivel(2300)).isEqualTo(2000);
        assertThat(regra.limiteDisponivel(999_999)).isEqualTo(2000);
    }

    /** Pontuacao negativa nao existe hoje (a penalidade de no-show e
     *  zero), mas a configuracao pode voltar a valer - e um limite
     *  negativo viraria mensagem sem sentido na tela. */
    @Test
    @DisplayName("pontuacao negativa vira limite zero, nao negativo")
    void pontuacaoNegativa() {
        assertThat(regra.limiteDisponivel(-500)).isZero();
    }

    /** O teto da nota e configuracao propria: se o professor soltar o
     *  teto de GANHO em palestra, o teto da NOTA continua onde esta. */
    @Test
    @DisplayName("o teto da nota e independente do teto de ganho")
    void tetoIndependente() {
        var soltoParaGanho = new RegraDistribuicao(1000, 2000);
        assertThat(soltoParaGanho.limiteDisponivel(5000))
                .as("ganhou 5000 em palestras, converte 2000")
                .isEqualTo(2000);
    }
}

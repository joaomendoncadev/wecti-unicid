package com.wecti.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A distribuicao do aluno com a conta fechada: o que ele pode usar, o
 * que ja usou e o que falta.
 *
 * <p>Os tres numeros vem prontos do servidor de proposito. A tela
 * poderia somar os itens sozinha, mas ai a regra do teto viveria em dois
 * lugares - e a tela e justamente onde o aluno confere se o sistema
 * errou.
 *
 * @param limite      quanto ele pode distribuir (pontuacao aparada em 2000)
 * @param distribuido soma do que ja colocou nas turmas
 * @param restante    {@code limite - distribuido}, nunca negativo
 * @param completa    {@code restante == 0}: so entao sai o comprovante
 * @param notaTotal   {@code distribuido} convertido em nota
 */
public record DistribuicaoResponse(
        int limite,
        int distribuido,
        int restante,
        boolean completa,
        BigDecimal notaTotal,
        int pontosPorNota,
        List<ItemDistribuicaoResponse> itens) {
}

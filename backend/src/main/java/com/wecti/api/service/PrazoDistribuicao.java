package com.wecti.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Janela em que o aluno pode distribuir a pontuacao em nota.
 *
 * <p>Pedido do professor em novembro de 2026: "Periodo para lancamento
 * da pontuacao 26/10/26 a 06/12/26". Depois do fim, o aluno nao altera
 * mais nada - os professores baixam as notas e lancam com seguranca,
 * sabendo que o numero nao muda debaixo deles.
 *
 * <p><b>O dia do fim esta DENTRO da janela.</b> "ate 06/12" significa o
 * dia 06 inteiro, nao as 00:00 dele - fechar a meia-noite do dia 06
 * roubaria um dia de quem deixou para a ultima hora, que e exatamente
 * quem mais precisa dele.
 *
 * <p><b>A impressao continua liberada depois do fim.</b> O que fecha e a
 * edicao; o comprovante e justamente o que o aluno leva ao professor, e
 * ele precisa poder imprimir de novo se perder o papel.
 *
 * <p>Mora num servico proprio, e nao espalhado em ifs, pelo mesmo motivo
 * de {@link PrazoInscricao}: tres pontos precisam concordar - o que a
 * tela mostra, o que a tela habilita e o que a API aceita. Se
 * divergirem, o aluno ve um botao que o servidor recusa.
 */
@Service
public class PrazoDistribuicao {

    private final LocalDate inicio;
    private final LocalDate fim;

    public PrazoDistribuicao(
            @Value("${app.distribuicao.inicio}")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @Value("${app.distribuicao.fim}")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        this.inicio = inicio;
        this.fim = fim;
    }

    public LocalDate inicio() {
        return inicio;
    }

    public LocalDate fim() {
        return fim;
    }

    /**
     * Dentro da janela? Comeca a 00:00 do dia de inicio e vai ate o fim
     * do dia de termino.
     */
    public boolean estaAberto() {
        return estaAbertoEm(LocalDateTime.now());
    }

    /** Separado para os testes poderem olhar qualquer instante sem
     *  depender do relogio da maquina. */
    boolean estaAbertoEm(LocalDateTime agora) {
        return !agora.isBefore(inicio.atStartOfDay())
                && !agora.isAfter(fim.atTime(23, 59, 59, 999_999_999));
    }
}

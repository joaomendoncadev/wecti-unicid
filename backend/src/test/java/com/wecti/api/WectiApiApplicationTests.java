package com.wecti.api;

import com.wecti.api.service.CodigoRotativoCheckin;
import com.wecti.api.service.RegraPontuacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que a aplicacao inteira sobe: todos os beans se resolvem, sem
 * dependencia faltando nem configuracao quebrada.
 *
 * Parece um teste bobo, mas e o que teria pego o erro de ObjectMapper que
 * derrubou o boot em producao - um bean que nao existia no classpath e so
 * estourava na hora de subir.
 *
 * Roda sobre H2 em memoria (ver application-test.yml), entao nao precisa
 * de MySQL nem de variavel de ambiente para rodar.
 */
@SpringBootTest
@ActiveProfiles("test")
class WectiApiApplicationTests {

    @Autowired private RegraPontuacao regraPontuacao;
    @Autowired private CodigoRotativoCheckin codigoRotativo;

    @Test
    @DisplayName("o contexto da aplicacao carrega sem erro")
    void contextLoads() {
    }

    /**
     * Os testes de regra injetam os valores a mao (para poder exercitar
     * qualquer configuracao), entao nenhum deles olha o que o
     * application.yml realmente traz. Este olha: o professor pediu
     * penalidade ZERO em setembro de 2026, e e este numero que vai valer
     * em producao se ninguem definir a variavel de ambiente.
     */
    @Test
    @DisplayName("os valores padrao de pontuacao sao os pedidos pelo professor")
    void padroesDePontuacao() {
        assertThat(regraPontuacao.penalidadeNoShow())
                .as("faltar nao tira mais ponto nenhum")
                .isZero();
        assertThat(regraPontuacao.limiteEventos())
                .as("teto de pontos ganhos em palestras")
                .isEqualTo(2000);
    }

    /**
     * Mesma ideia do teste acima, para a janela do QR: os testes de regra
     * injetam o valor a mao, entao nenhum deles olha o que o
     * application.yml realmente traz. O professor pediu 15 minutos em
     * setembro de 2026.
     */
    @Test
    @DisplayName("a janela do QR de check-in e de 15 minutos")
    void janelaDoQrCode() {
        assertThat(codigoRotativo.janelaSegundos())
                .as("15 minutos - o minimo garantido de validade para o aluno")
                .isEqualTo(900);
    }
}

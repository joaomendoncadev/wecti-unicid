package com.wecti.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O limite de tentativas derrubou o primeiro dia da WECTI 2026: era por
 * IP, e como a turma inteira sai pelo mesmo IP do Wi-Fi do campus, dez
 * alunos errando a propria senha bloqueavam todos os outros - inclusive
 * no "Esqueci minha senha". 42 alunos ficaram sem conseguir entrar.
 *
 * <p>Estes testes existem para que isso nao volte: o que vale e a CONTA,
 * nunca a origem da requisicao.
 */
class LimiteTentativasPorContaTest {

    private static final int MAX = 5;
    private final LimiteTentativasPorConta limite = new LimiteTentativasPorConta(MAX, 15);

    @Test
    @DisplayName("bloqueia a conta que errou, e SO ela")
    void bloqueiaApenasAContaQueErrou() {
        for (int i = 0; i < MAX; i++) {
            limite.registrarFalha("esquecido@unicid.test");
        }

        assertThat(limite.bloqueada("esquecido@unicid.test")).isTrue();
        assertThat(limite.bloqueada("outro@unicid.test"))
                .as("este e o bug que derrubou o evento: um aluno errando nao pode "
                        + "travar os colegas do mesmo Wi-Fi")
                .isFalse();
    }

    @Test
    @DisplayName("muitos alunos diferentes errando nao bloqueiam ninguem")
    void falhasEspalhadasNaoBloqueiam() {
        // Cenario real: 40 pessoas, cada uma errando a propria senha uma
        // vez. Por IP isso teria bloqueado a sala inteira.
        for (int i = 0; i < 40; i++) {
            limite.registrarFalha("aluno" + i + "@unicid.test");
        }

        for (int i = 0; i < 40; i++) {
            assertThat(limite.bloqueada("aluno" + i + "@unicid.test")).isFalse();
        }
    }

    @Test
    @DisplayName("acertar a senha zera o historico de falhas")
    void sucessoZeraOContador() {
        for (int i = 0; i < MAX - 1; i++) {
            limite.registrarFalha("ana@unicid.test");
        }
        limite.registrarSucesso("ana@unicid.test");
        limite.registrarFalha("ana@unicid.test");

        assertThat(limite.bloqueada("ana@unicid.test"))
                .as("sem zerar, quem errasse 4 vezes e acertasse ficaria a uma "
                        + "falha do bloqueio na proxima vez que entrasse")
                .isFalse();
    }

    @Test
    @DisplayName("e-mail com maiuscula ou espaco cai no mesmo contador")
    void chaveNormalizada() {
        for (int i = 0; i < MAX; i++) {
            limite.registrarFalha("Ana@Unicid.test");
        }

        assertThat(limite.bloqueada(" ana@unicid.test "))
                .as("sem normalizar, trocar a caixa do e-mail daria um contador "
                        + "novo e dobraria as tentativas disponiveis")
                .isTrue();
    }

    @Test
    @DisplayName("abaixo do limite nao bloqueia")
    void abaixoDoLimiteLibera() {
        for (int i = 0; i < MAX - 1; i++) {
            limite.registrarFalha("bruno@unicid.test");
        }

        assertThat(limite.bloqueada("bruno@unicid.test")).isFalse();
    }

    @Test
    @DisplayName("informa quanto falta para liberar")
    void informaEspera() {
        for (int i = 0; i < MAX; i++) {
            limite.registrarFalha("carla@unicid.test");
        }

        assertThat(limite.segundosAteLiberar("carla@unicid.test"))
                .isGreaterThan(0)
                .isLessThanOrEqualTo(15 * 60);
    }
}

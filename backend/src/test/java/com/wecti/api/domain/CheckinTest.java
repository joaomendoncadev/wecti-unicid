package com.wecti.api.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regra de presenca qualificada: <b>os dois QR codes lidos</b> - entrada
 * e saida. E o criterio que decide TANTO o certificado quanto a
 * pontuacao, entao errar aqui reprova aluno que compareceu ou premia
 * quem nao foi.
 *
 * <p><b>Ate setembro de 2026 exigia tambem permanencia >= 75% da duracao
 * do evento.</b> O professor tirou a regra: agora a leitura dos dois QRs
 * e a prova de presenca, e o tempo que o aluno ficou na sala nao entra
 * mais na conta. Os testes de percentual que existiam aqui sairam junto -
 * cobriam uma regra que nao existe mais.
 */
class CheckinTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 8, 20, 19, 0);

    private Checkin checkin(LocalDateTime entrada, LocalDateTime saida) {
        return Checkin.builder().entrada(entrada).saida(saida).build();
    }

    @Test
    @DisplayName("entrada e saida registradas: presenca qualificada")
    void entradaESaidaQualificam() {
        assertThat(checkin(INICIO, INICIO.plusHours(2)).isPresencaQualificada()).isTrue();
    }

    @Test
    @DisplayName("sem check-out nao qualifica - e o unico jeito de nao cumprir o criterio")
    void semCheckoutNaoQualifica() {
        assertThat(checkin(INICIO, null).isPresencaQualificada()).isFalse();
    }

    @Test
    @DisplayName("tempo curto na sala qualifica igual: nao ha mais minimo de permanencia")
    void tempoNaSalaNaoImportaMais() {
        LocalDateTime saida = INICIO.plusMinutes(2);

        assertThat(checkin(INICIO, saida).isPresencaQualificada())
                .as("2 minutos de 2 horas reprovava na regra antiga (1,6%%); hoje o que "
                        + "vale e ter lido os dois QRs")
                .isTrue();
    }

    @Test
    @DisplayName("marcar entrada e saida fora do horario do evento tambem qualifica")
    void foraDoHorarioTambemQualifica() {
        // O check-in abre 1h antes e fecha 30 min depois do fim, entao
        // isso e possivel na pratica. A regra antiga recortava a
        // permanencia pelo horario do evento; hoje nao ha o que recortar.
        LocalDateTime entrada = INICIO.minusMinutes(50);
        LocalDateTime saida = INICIO.minusMinutes(10);

        assertThat(checkin(entrada, saida).isPresencaQualificada()).isTrue();
    }
}

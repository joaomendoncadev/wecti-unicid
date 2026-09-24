package com.wecti.api.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.TimeZone;

/**
 * Fixa o fuso horario da aplicacao.
 *
 * <p><b>Por que isso importa aqui mais do que no projeto medio.</b>
 * Praticamente toda regra deste sistema e um horario comparado com "o
 * agora": se a inscricao ainda esta aberta, se a janela de check-in
 * abriu, se o evento terminou (e portanto vale ponto ou no-show).
 * Todas usam {@code LocalDateTime.now()}, que le o fuso <b>padrao da
 * JVM</b>.
 *
 * <p>O {@code serverTimezone} na URL do JDBC nao resolve isso: ele so diz
 * ao driver como converter datas na conversa com o MySQL. Numa hospedagem
 * configurada em UTC, o Java acharia que sao 3 horas mais tarde do que
 * realmente e no Brasil - e um evento marcado para as 19h ficaria
 * "encerrado" as 16h, sem ninguem entender por que sumiu da tela.
 *
 * <p>O admin digita horarios locais no formulario e eles sao gravados sem
 * fuso ({@code LocalDateTime}). Entao o unico jeito de a comparacao fazer
 * sentido e a aplicacao pensar no mesmo fuso em que esses horarios foram
 * escritos.
 */
@Configuration
public class FusoHorarioConfig {

    private static final Logger log = LoggerFactory.getLogger(FusoHorarioConfig.class);

    private final String fuso;

    public FusoHorarioConfig(@Value("${app.fuso-horario:America/Sao_Paulo}") String fuso) {
        this.fuso = fuso;
    }

    @PostConstruct
    void aplicar() {
        TimeZone anterior = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(fuso));
        log.info("Fuso horario da aplicacao: {} (o servidor estava em {}). Agora sao {}.",
                fuso, anterior.getID(), LocalDateTime.now());
    }
}

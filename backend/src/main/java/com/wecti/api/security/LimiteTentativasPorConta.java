package com.wecti.api.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Trava forca bruta <b>por conta</b>, e nao por IP.
 *
 * <p><b>Por que nao por IP.</b> Foi o que derrubou o primeiro dia da
 * WECTI 2026 (28/09). A turma inteira acessa pelo Wi-Fi do campus e sai
 * com o MESMO IP publico - e como o limite era por IP, bastavam 10
 * alunos errando a propria senha para bloquear TODOS os outros por 15
 * minutos. O bloqueio pegava tambem o "Esqueci minha senha", entao quem
 * tentava se recuperar ficava presente na sala, sem conseguir entrar no
 * sistema, e perdia o prazo de inscricao. Foram 42 alunos afetados.
 *
 * <p>A versao anterior tinha um comentario dizendo que contar apenas as
 * falhas resolvia esse cenario. <b>Nao resolvia:</b> num evento com
 * centenas de pessoas, dez falhas legitimas acontecem em segundos.
 *
 * <p><b>Como fica.</b> A chave e o e-mail informado. Quem fica tentando
 * adivinhar a senha de uma conta e barrado naquela conta, e ninguem mais
 * e afetado. Um acerto zera o contador - antes ele so vencia pelo tempo,
 * entao quem errasse nove vezes e acertasse na decima ficava a uma falha
 * de ser bloqueado no dia seguinte.
 *
 * <p><b>O que isto nao cobre.</b> Um atacante testando UMA senha comum
 * em MUITAS contas (password spraying) nao e barrado, porque cada conta
 * tem seu proprio contador. Para o tamanho e a duracao deste sistema, o
 * risco e muito menor do que o de travar a sala inteira - que foi o que
 * de fato aconteceu.
 *
 * <p>O estado fica em memoria, o que basta para uma instancia. Com mais
 * de uma, cada uma teria seu contador e o limite efetivo seria
 * multiplicado; nesse caso isto precisa ir para um armazenamento
 * compartilhado.
 */
@Component
public class LimiteTentativasPorConta {

    private static final Logger log = LoggerFactory.getLogger(LimiteTentativasPorConta.class);

    /** Limpa registros vencidos quando o mapa passa desse tamanho, pra
     *  memoria nao crescer sem limite. */
    private static final int LIMITE_PARA_LIMPEZA = 10_000;

    private final int maxFalhas;
    private final Duration janela;
    private final Map<String, Tentativas> porConta = new ConcurrentHashMap<>();

    public LimiteTentativasPorConta(
            @Value("${app.rate-limit.max-falhas}") int maxFalhas,
            @Value("${app.rate-limit.janela-minutos}") long janelaMinutos) {
        this.maxFalhas = maxFalhas;
        this.janela = Duration.ofMinutes(janelaMinutos);
    }

    /** Mesma normalizacao do banco (collation utf8mb4_unicode_ci), para
     *  que "Joao@x.com" e "joao@x.com " caiam no mesmo contador e nao
     *  dobrem as tentativas disponiveis. */
    private String chave(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean bloqueada(String email) {
        Tentativas t = porConta.get(chave(email));
        return t != null && t.bloqueado(maxFalhas, janela);
    }

    public long segundosAteLiberar(String email) {
        Tentativas t = porConta.get(chave(email));
        return t == null ? 0 : t.segundosAteLiberar(janela);
    }

    public void registrarFalha(String email) {
        String chave = chave(email);
        if (porConta.size() > LIMITE_PARA_LIMPEZA) {
            porConta.values().removeIf(t -> t.expirou(janela));
        }
        Tentativas t = porConta.compute(chave, (k, atual) ->
                (atual == null || atual.expirou(janela)) ? new Tentativas() : atual.incrementar());
        if (t.contador.get() >= maxFalhas) {
            log.warn("Conta {} bloqueada por {} falhas seguidas", chave, t.contador.get());
        }
    }

    /** Sucesso limpa o historico: quem acertou a senha nao deve carregar
     *  as tentativas anteriores para a proxima vez que entrar. */
    public void registrarSucesso(String email) {
        porConta.remove(chave(email));
    }

    private static final class Tentativas {
        private final AtomicInteger contador = new AtomicInteger(1);
        private final Instant primeiraFalha = Instant.now();

        Tentativas incrementar() {
            contador.incrementAndGet();
            return this;
        }

        boolean expirou(Duration janela) {
            return Instant.now().isAfter(primeiraFalha.plus(janela));
        }

        boolean bloqueado(int maxFalhas, Duration janela) {
            return !expirou(janela) && contador.get() >= maxFalhas;
        }

        long segundosAteLiberar(Duration janela) {
            return Math.max(1, Duration.between(Instant.now(), primeiraFalha.plus(janela)).toSeconds());
        }
    }
}

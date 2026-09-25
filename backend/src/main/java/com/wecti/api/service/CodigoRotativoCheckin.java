package com.wecti.api.service;

import com.wecti.api.domain.SessaoCheckin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Codigo de curta duracao embutido no QR de check-in, no mesmo espirito
 * de um token TOTP.
 *
 * <p><b>Por que existe.</b> O QR e projetado na tela e e o mesmo pra sala
 * inteira - por definicao nao da pra ser individual. Enquanto o link era
 * fixo, bastava um aluno presente fotografar a tela e mandar no grupo:
 * quem nao veio abria o link de casa e marcava presenca. Nao havia nada
 * no servidor capaz de distinguir os dois casos.
 *
 * <p><b>Como resolve.</b> O codigo e derivado do segredo da sessao e da
 * janela de tempo atual, entao ele muda sozinho a cada janela (padrao:
 * 900 segundos) sem precisar gravar nada no banco. A tela do admin regera
 * o QR no mesmo ritmo. Um print compartilhado morre junto com a janela em
 * que foi tirado - o link que ele carrega para de ser aceito.
 *
 * <p>A janela anterior tambem e aceita. Sem isso, quem escaneasse um
 * segundo antes da troca levaria "QR invalido" no meio de um check-in
 * legitimo. Na pratica o codigo vive entre 1 e 2 janelas - com 900s, de
 * 15 a 30 minutos.
 *
 * <p><b>As janelas sao alinhadas ao relogio absoluto</b>
 * ({@code epochSecond / janelaSegundos}), e nao ao instante em que a
 * sessao foi criada: com 900s, a troca cai em :00, :15, :30 e :45. Por
 * isso a PRIMEIRA exibicao de um QR recem-gerado costuma durar menos que
 * uma janela inteira - mas o codigo que o aluno ja fotografou continua
 * aceito pela janela seguinte, entao o minimo garantido de validade e
 * sempre uma janela cheia.
 *
 * <p><b>Isto deixou de ser uma defesa.</b> O professor decidiu, em
 * setembro de 2026, nao tentar impedir que os alunos repassem foto do QR
 * ("nao temos como controlar isso") e pediu 15 minutos de validade. A
 * rotacao continua porque custa zero e ainda limita o estrago de um link
 * vazado, mas o que realmente fecha a porta e a sessao expirar 30 min
 * depois do fim da palestra.
 */
@Service
public class CodigoRotativoCheckin {

    /** 5 bytes = 10 caracteres hex. Espaco grande demais pra ser chutado
     *  dentro de uma janela de um minuto, e curto o bastante pra nao
     *  inchar o QR (mais dados = QR mais denso e mais dificil de ler
     *  projetado). */
    private static final int BYTES_DO_CODIGO = 5;

    private static final String ALGORITMO = "HmacSHA256";

    private final SecureRandom random = new SecureRandom();
    private final long janelaSegundos;
    private final Clock relogio;

    @Autowired
    public CodigoRotativoCheckin(@Value("${app.checkin.janela-codigo-segundos}") long janelaSegundos) {
        this(janelaSegundos, Clock.systemDefaultZone());
    }

    /** O relogio e injetavel so pra que o teste consiga andar no tempo e
     *  verificar que um codigo de janela passada realmente para de ser
     *  aceito - que e a garantia inteira desta classe. */
    CodigoRotativoCheckin(long janelaSegundos, Clock relogio) {
        if (janelaSegundos <= 0) {
            throw new IllegalArgumentException("app.checkin.janela-codigo-segundos precisa ser maior que zero");
        }
        this.janelaSegundos = janelaSegundos;
        this.relogio = relogio;
    }

    /** Segredo aleatorio de uma sessao nova. Fica so no banco - nunca
     *  entra em DTO nem no conteudo do QR. */
    public String gerarSegredo() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Codigo que deve ir no QR agora. */
    public String codigoAtual(SessaoCheckin sessao) {
        return calcular(sessao.getSegredo(), janelaAtual());
    }

    /**
     * Aceita o codigo da janela atual e o da anterior. Comparacao em
     * tempo constante ({@link MessageDigest#isEqual}) pra nao vazar,
     * pelo tempo de resposta, o quanto de um palpite estava certo.
     */
    public boolean aceita(SessaoCheckin sessao, String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return false;
        }
        byte[] recebido = codigo.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        long janela = janelaAtual();
        return confere(recebido, sessao.getSegredo(), janela)
                || confere(recebido, sessao.getSegredo(), janela - 1);
    }

    /** Duracao de uma janela inteira. A tela usa como denominador da
     *  barra de tempo em volta do QR - ver QrCodeSessaoResponse. */
    public long janelaSegundos() {
        return janelaSegundos;
    }

    /** Quando o codigo atual deixa de ser o exibido - e a hora em que a
     *  tela do admin precisa trocar o QR. */
    public LocalDateTime fimDaJanelaAtual() {
        long fimEmSegundos = (janelaAtual() + 1) * janelaSegundos;
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(fimEmSegundos), relogio.getZone());
    }

    private long janelaAtual() {
        return relogio.instant().getEpochSecond() / janelaSegundos;
    }

    private boolean confere(byte[] recebido, String segredo, long janela) {
        byte[] esperado = calcular(segredo, janela).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(esperado, recebido);
    }

    private String calcular(String segredo, long janela) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            byte[] hash = mac.doFinal(Long.toString(janela).getBytes(StandardCharsets.UTF_8));
            byte[] recorte = new byte[BYTES_DO_CODIGO];
            System.arraycopy(hash, 0, recorte, 0, BYTES_DO_CODIGO);
            return HexFormat.of().formatHex(recorte);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Falha ao calcular o codigo rotativo do check-in", ex);
        }
    }
}

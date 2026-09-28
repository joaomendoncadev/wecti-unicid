package com.wecti.api.exception;

/**
 * Conta bloqueada por falhas seguidas de autenticacao (ver
 * {@code LimiteTentativasPorConta}). Vira 429 no
 * {@link GlobalExceptionHandler}.
 */
public class MuitasTentativasException extends RuntimeException {

    private final long segundosAteLiberar;

    public MuitasTentativasException(long segundosAteLiberar) {
        super(montarMensagem(segundosAteLiberar));
        this.segundosAteLiberar = segundosAteLiberar;
    }

    /**
     * Arredonda para CIMA, e usa segundos abaixo de um minuto.
     *
     * <p>Antes dividia por 60 arredondando para baixo, o que era
     * inofensivo com a janela de 15 minutos (errava no maximo 59s de
     * 900). Com a janela em 2 minutos a conta passou a mentir na metade
     * dos casos: faltando 110s ela dizia "aguarde 1 minuto", o aluno
     * voltava no tempo que a tela mandou e continuava bloqueado - o que,
     * num evento, vira a mesma frustracao que a gente acabou de
     * resolver.
     */
    private static String montarMensagem(long segundos) {
        String espera = segundos < 60
                ? segundos + " segundos"
                : ((segundos + 59) / 60) + " minuto(s)";
        return "Muitas tentativas seguidas nesta conta. Aguarde " + espera + " e tente de novo.";
    }

    public long getSegundosAteLiberar() {
        return segundosAteLiberar;
    }
}

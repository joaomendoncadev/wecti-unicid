package com.wecti.api.domain;

/**
 * Turno em que a turma acontece. Guardado sem acento (MANHA) porque e
 * valor de enum em coluna de banco; a tela mostra "Manha"/"Noite".
 *
 * <p>Nao se confunde com o turno do EVENTO, que nao e armazenado: aquele
 * e derivado do horario de inicio da palestra, so para agrupar a lista
 * (ver turnoDoEvento no frontend). Aqui e dado vindo da planilha da
 * coordenacao.
 */
public enum TurnoDisciplina {
    MANHA,
    NOITE
}

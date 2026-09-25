package com.wecti.api.dto;

import java.time.LocalDateTime;

/**
 * O QR da vez, pra tela do admin.
 *
 * <p>Antes esse endpoint devolvia o PNG cru. Agora devolve JSON porque o
 * conteudo do QR nao e mais fixo: ele carrega um codigo que rotaciona, e
 * a tela precisa saber <b>quando</b> buscar o proximo - e isso que
 * {@code codigoExpiraEm} informa. Sem esse dado, a tela teria que chutar
 * o ritmo e acabaria mostrando um QR ja vencido.
 *
 * @param pngBase64      imagem do QR, pronta pra um {@code src="data:image/png;base64,..."}
 * @param codigoExpiraEm quando este QR deixa de ser o exibido - a tela busca o proximo aqui
 * @param janelaSegundos duracao de uma janela inteira. Vai junto porque a
 *                       barra de tempo da tela precisa de um denominador
 *                       fixo: as janelas sao alinhadas ao relogio, entao a
 *                       primeira exibicao costuma ser um pedaco de janela
 *                       (ex.: 40s de 900s) e sem isto a barra comecaria
 *                       cheia e despencaria, mentindo sobre o tempo
 * @param sessaoExpiraEm fim da janela de check-in do evento; passou disso, nao adianta gerar outro
 */
public record QrCodeSessaoResponse(
        String pngBase64,
        LocalDateTime codigoExpiraEm,
        long janelaSegundos,
        LocalDateTime sessaoExpiraEm) {
}

package com.wecti.api.controller;

import com.wecti.api.dto.CheckinResponse;
import com.wecti.api.dto.ConfirmarCheckinRequest;
import com.wecti.api.dto.EventoCheckinResponse;
import com.wecti.api.dto.NovaSessaoCheckinRequest;
import com.wecti.api.dto.QrCodeSessaoResponse;
import com.wecti.api.dto.SessaoCheckinResponse;
import com.wecti.api.security.AuthenticatedUser;
import com.wecti.api.service.CheckinSessaoService;
import com.wecti.api.service.CheckinService;
import com.wecti.api.service.CodigoRotativoCheckin;
import com.wecti.api.service.EventoService;
import com.wecti.api.service.QrCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

@RestController
public class CheckinSessaoController {

    private final CheckinSessaoService checkinSessaoService;
    private final CheckinService checkinService;
    private final EventoService eventoService;
    private final QrCodeService qrCodeService;
    private final CodigoRotativoCheckin codigoRotativo;
    private final String frontendUrl;

    public CheckinSessaoController(CheckinSessaoService checkinSessaoService, CheckinService checkinService,
                                    EventoService eventoService, QrCodeService qrCodeService,
                                    CodigoRotativoCheckin codigoRotativo,
                                    @Value("${app.frontend-url}") String frontendUrl) {
        this.checkinSessaoService = checkinSessaoService;
        this.checkinService = checkinService;
        this.eventoService = eventoService;
        this.qrCodeService = qrCodeService;
        this.codigoRotativo = codigoRotativo;
        this.frontendUrl = frontendUrl;
    }

    /** Relatório de presença (AdminCheckinPage - "Participantes do
     *  Evento") - lista quem já fez check-in nesse evento, com nome/RGM. */
    @GetMapping("/eventos/{eventoId}/checkins")
    public List<EventoCheckinResponse> listarCheckinsDoEvento(@PathVariable UUID eventoId) {
        var evento = eventoService.buscarPorId(eventoId);
        return checkinService.listarPorEvento(eventoId).stream()
                .map(checkin -> EventoCheckinResponse.de(checkin, evento))
                .toList();
    }

    @PostMapping("/eventos/{eventoId}/checkin-sessoes")
    public ResponseEntity<SessaoCheckinResponse> criar(@PathVariable UUID eventoId,
                                                        @Valid @RequestBody NovaSessaoCheckinRequest request) {
        var sessao = checkinSessaoService.criar(eventoId, request.tipo());
        return ResponseEntity.status(HttpStatus.CREATED).body(SessaoCheckinResponse.de(sessao));
    }

    /**
     * QR da janela atual. A tela do admin chama isso em loop, uma vez por
     * janela (ver {@code codigo_expira_em} na resposta) - o conteúdo muda
     * a cada chamada porque o código embutido rotaciona, e é isso que
     * impede que um print da tela mandado no grupo continue valendo.
     *
     * <p>O link precisa ser um endereço que o CELULAR de quem escaneia
     * consiga abrir - "app.frontend-url" fixo (default
     * http://localhost:5173) só funciona quando quem escaneia é a MESMA
     * máquina que roda o backend, o que nunca é o caso na prática (é
     * sempre outro aparelho). Em vez disso, usamos o header Origin da
     * própria requisição: é o admin logado, pelo navegador, quem está
     * pedindo esse QR code pra projetar na tela - o Origin dessa
     * requisição É exatamente a URL do site que ele está usando agora
     * (http://192.168.x.x:5173 numa rede local, https://dominio-real.com
     * em produção), então é isso que o aluno também vai conseguir abrir.
     * "app.frontend-url" fica só como fallback pra chamadas sem Origin
     * (raro num navegador de verdade). Confiável aqui porque o CORS já
     * filtrou quais Origins conseguem completar essa chamada autenticada
     * antes desse código rodar.
     */
    @GetMapping("/checkin-sessoes/{sessaoId}/qrcode")
    public QrCodeSessaoResponse qrcode(@PathVariable UUID sessaoId, HttpServletRequest request) {
        var sessao = checkinSessaoService.buscarValida(sessaoId);
        String origin = request.getHeader("Origin");
        String base = (origin != null && !origin.isBlank()) ? origin : frontendUrl;
        String url = base + "/checkin/confirmar/" + sessaoId + "?c=" + codigoRotativo.codigoAtual(sessao);
        String png = Base64.getEncoder().encodeToString(qrCodeService.gerarPng(url));
        return new QrCodeSessaoResponse(png, codigoRotativo.fimDaJanelaAtual(),
                codigoRotativo.janelaSegundos(), sessao.getExpiraEm());
    }

    @PostMapping("/checkin-sessoes/{sessaoId}/confirmar")
    public CheckinResponse confirmar(@PathVariable UUID sessaoId,
                                      @Valid @RequestBody ConfirmarCheckinRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser autenticado) {
        var checkin = checkinSessaoService.confirmar(sessaoId, autenticado.id(), request.codigo());
        return CheckinResponse.de(checkin, checkin.getInscricao().getEvento());
    }
}

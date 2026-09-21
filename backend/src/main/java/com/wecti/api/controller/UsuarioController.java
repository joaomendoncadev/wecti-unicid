package com.wecti.api.controller;

import com.wecti.api.domain.Perfil;
import com.wecti.api.dto.AtualizarPerfilRequest;
import com.wecti.api.dto.NovoUsuarioRequest;
import com.wecti.api.dto.PaginaResponse;
import com.wecti.api.dto.UsuarioResponse;
import com.wecti.api.security.AuthenticatedUser;
import com.wecti.api.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/usuarios")
    public PaginaResponse<UsuarioResponse> listar(@RequestParam(required = false) Perfil perfil,
                                                   @RequestParam(required = false) String busca,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("nome").ascending());
        return PaginaResponse.de(usuarioService.listar(perfil, busca, pageable), UsuarioResponse::de);
    }

    @PostMapping("/usuarios")
    public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody NovoUsuarioRequest request) {
        var usuario = usuarioService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.de(usuario));
    }

    /** O id do autenticado vai junto para o service barrar o admin de
     *  remover o proprio acesso (ver UsuarioService.atualizar). */
    @PutMapping("/usuarios/{usuarioId}")
    public UsuarioResponse atualizar(@PathVariable UUID usuarioId,
                                      @Valid @RequestBody NovoUsuarioRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser autenticado) {
        return UsuarioResponse.de(usuarioService.atualizar(usuarioId, request, autenticado.id()));
    }

    @DeleteMapping("/usuarios/{usuarioId}")
    public ResponseEntity<Void> excluir(@PathVariable UUID usuarioId,
                                         @AuthenticationPrincipal AuthenticatedUser autenticado) {
        usuarioService.excluir(usuarioId, autenticado.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/usuarios/me")
    public UsuarioResponse me(@AuthenticationPrincipal AuthenticatedUser autenticado) {
        return UsuarioResponse.de(usuarioService.buscarPorId(autenticado.id()));
    }

    /**
     * A propria pessoa corrigindo o cadastro. O id vem do token, nunca
     * do caminho ou do corpo - assim nao ha como editar o perfil de
     * outra pessoa passando o id dela.
     */
    @PutMapping("/usuarios/me")
    public UsuarioResponse atualizarMeuPerfil(@AuthenticationPrincipal AuthenticatedUser autenticado,
                                               @Valid @RequestBody AtualizarPerfilRequest request) {
        return UsuarioResponse.de(usuarioService.atualizarMeuPerfil(autenticado.id(), request));
    }
}

package com.wecti.api.service;

import com.wecti.api.dto.CadastroAlunoRequest;
import com.wecti.api.dto.LoginRequest;
import com.wecti.api.dto.LoginResponse;
import com.wecti.api.dto.RedefinirSenhaRequest;
import com.wecti.api.dto.UsuarioResponse;
import com.wecti.api.exception.CredenciaisInvalidasException;
import com.wecti.api.exception.MuitasTentativasException;
import com.wecti.api.security.LimiteTentativasPorConta;
import com.wecti.api.repository.UsuarioRepository;
import com.wecti.api.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UsuarioService usuarioService;
    private final LimiteTentativasPorConta limiteTentativas;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                        UsuarioService usuarioService, LimiteTentativasPorConta limiteTentativas) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.usuarioService = usuarioService;
        this.limiteTentativas = limiteTentativas;
    }

    public LoginResponse login(LoginRequest request) {
        exigirContaLiberada(request.email());

        var usuario = usuarioRepository.findByEmail(request.email()).orElse(null);
        if (usuario == null || !passwordEncoder.matches(request.senha(), usuario.getSenha())) {
            limiteTentativas.registrarFalha(request.email());
            throw new CredenciaisInvalidasException("Email ou senha invalidos");
        }

        limiteTentativas.registrarSucesso(request.email());
        String token = jwtService.gerarToken(usuario);
        return new LoginResponse(token, UsuarioResponse.de(usuario));
    }

    /** O limite e por CONTA, nao por IP - ver LimiteTentativasPorConta
     *  para o porque (o Wi-Fi do campus poe a turma inteira no mesmo
     *  IP). Checado antes de tocar o banco, para uma rajada de
     *  tentativas nao virar carga de consulta. */
    private void exigirContaLiberada(String email) {
        if (limiteTentativas.bloqueada(email)) {
            throw new MuitasTentativasException(limiteTentativas.segundosAteLiberar(email));
        }
    }

    /** Cadastro publico (sempre ALUNO - ver UsuarioService.registrarAluno)
     *  ja devolve token, pra entrar direto no sistema sem precisar de um
     *  segundo login logo em seguida. */
    public LoginResponse registrar(CadastroAlunoRequest request) {
        var usuario = usuarioService.registrarAluno(request);
        String token = jwtService.gerarToken(usuario);
        return new LoginResponse(token, UsuarioResponse.de(usuario));
    }

    /** "Esqueci minha senha" (ver UsuarioService.redefinirSenha) - ja
     *  devolve token, entao a pessoa entra direto com a senha nova. */
    public LoginResponse redefinirSenha(RedefinirSenhaRequest request) {
        exigirContaLiberada(request.email());
        try {
            var usuario = usuarioService.redefinirSenha(request);
            limiteTentativas.registrarSucesso(request.email());
            String token = jwtService.gerarToken(usuario);
            return new LoginResponse(token, UsuarioResponse.de(usuario));
        } catch (CredenciaisInvalidasException e) {
            // Errar o RGM aqui e uma tentativa de adivinhacao como
            // outra qualquer, entao conta - mas na conta alvo, nao no
            // IP de quem tentou.
            limiteTentativas.registrarFalha(request.email());
            throw e;
        }
    }
}

package com.wecti.api.service;

import com.wecti.api.domain.Perfil;
import com.wecti.api.domain.Usuario;
import com.wecti.api.dto.AtualizarPerfilRequest;
import com.wecti.api.dto.CadastroAlunoRequest;
import com.wecti.api.dto.NovoUsuarioRequest;
import com.wecti.api.dto.RedefinirSenhaRequest;
import com.wecti.api.exception.CampoInvalidoException;
import com.wecti.api.exception.ConflitoException;
import com.wecti.api.exception.CredenciaisInvalidasException;
import com.wecti.api.exception.RecursoNaoEncontradoException;
import com.wecti.api.exception.RegraNegocioException;
import com.wecti.api.repository.InscricaoRepository;
import com.wecti.api.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final InscricaoRepository inscricaoRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, InscricaoRepository inscricaoRepository,
                           PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.inscricaoRepository = inscricaoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Filtro por perfil + busca livre (nome/email/rgm/cpf) combinados via
     * Specification, com paginacao - a tela de Usuarios (admin) vai
     * crescer muito quando o sistema for liberado pros alunos, listar
     * tudo de uma vez nao escala.
     */
    public Page<Usuario> listar(Perfil perfil, String busca, Pageable pageable) {
        Specification<Usuario> spec = (root, query, cb) -> cb.conjunction();

        if (perfil != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("perfil"), perfil));
        }
        if (busca != null && !busca.isBlank()) {
            String termo = "%" + busca.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nome")), termo),
                    cb.like(cb.lower(root.get("email")), termo),
                    cb.like(root.get("rgm"), termo),
                    cb.like(root.get("cpf"), termo)));
        }

        return usuarioRepository.findAll(spec, pageable);
    }

    public Usuario buscarPorId(UUID id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuario nao encontrado: " + id));
    }

    public Usuario criar(NovoUsuarioRequest request) {
        validarIdentificadoresObrigatorios(request);
        validarEmailUnico(request.email(), null);
        validarRgmUnico(request.rgm(), null);
        validarCpfUnico(request.cpf(), null);

        String senhaProvisoria = UUID.randomUUID().toString();
        Usuario usuario = Usuario.builder()
                .nome(request.nome())
                .email(request.email())
                .perfil(request.perfil())
                .rgm(request.perfil() == Perfil.ALUNO ? request.rgm() : null)
                .cpf(request.perfil() == Perfil.ADMIN ? request.cpf() : null)
                .curso(request.perfil() == Perfil.ALUNO ? request.curso() : null)
                .senha(passwordEncoder.encode(senhaProvisoria))
                .build();

        return usuarioRepository.save(usuario);
    }

    /**
     * Tela de Usuarios do admin: edita qualquer cadastro, inclusive o
     * perfil e o email - campos que o proprio dono nao mexe sozinho (ver
     * {@link #atualizarMeuPerfil}).
     *
     * @param solicitanteId o admin que esta fazendo a alteracao, para a
     *                      trava de auto-rebaixamento abaixo
     */
    public Usuario atualizar(UUID id, NovoUsuarioRequest request, UUID solicitanteId) {
        Usuario usuario = buscarPorId(id);

        // So admin cria admin. Se o admin logado se rebaixasse a ALUNO,
        // nao haveria ninguem para promove-lo de volta - o sistema
        // ficaria sem administrador e so um acesso direto ao banco
        // resolveria. Ele pode rebaixar OUTRO admin; isso sempre deixa
        // pelo menos um de pe, que e ele mesmo.
        if (id.equals(solicitanteId) && request.perfil() != Perfil.ADMIN) {
            throw new RegraNegocioException(
                    "Voce nao pode remover o proprio acesso de administrador. "
                            + "Peca a outro administrador para fazer essa alteracao.");
        }

        validarIdentificadoresObrigatorios(request);
        validarEmailUnico(request.email(), id);
        validarRgmUnico(request.rgm(), id);
        validarCpfUnico(request.cpf(), id);

        usuario.setNome(request.nome());
        usuario.setEmail(request.email());
        usuario.setPerfil(request.perfil());
        usuario.setRgm(request.perfil() == Perfil.ALUNO ? request.rgm() : null);
        usuario.setCpf(request.perfil() == Perfil.ADMIN ? request.cpf() : null);
        usuario.setCurso(request.perfil() == Perfil.ALUNO ? request.curso() : null);

        return usuarioRepository.save(usuario);
    }

    /**
     * A propria pessoa corrigindo o cadastro (PUT /usuarios/me). Nasceu
     * de um problema real: muito aluno errou nome, RGM ou curso no
     * autocadastro e nao tinha como arrumar - sobrava para o admin, um a
     * um. Vale para os dois perfis: o admin usa a mesma tela para os
     * dados dele.
     *
     * <p>Cada perfil mexe no que e seu, com a mesma regra de {@link #criar}:
     * ALUNO em rgm e curso, ADMIN em cpf. O campo do outro perfil vem no
     * corpo mas e ignorado, entao nao ha como um aluno se dar um CPF nem
     * um admin se dar um RGM.
     *
     * <p>Email, perfil e senha ficam de fora - ver o javadoc do DTO para
     * o motivo de cada um. Em especial, <b>o perfil nunca e tocado
     * aqui</b>: e o que impede um aluno de virar admin mandando JSON.
     */
    public Usuario atualizarMeuPerfil(UUID id, AtualizarPerfilRequest request) {
        Usuario usuario = buscarPorId(id);

        usuario.setNome(request.nome());

        if (usuario.getPerfil() == Perfil.ALUNO) {
            if (request.rgm() == null || request.rgm().isBlank()) {
                throw new CampoInvalidoException("rgm", "RGM e obrigatorio para usuarios com perfil ALUNO");
            }
            validarRgmUnico(request.rgm(), id);
            usuario.setRgm(request.rgm());
            usuario.setCurso(request.curso());
        } else if (usuario.getPerfil() == Perfil.ADMIN) {
            // Mesma exigencia de criar(): sem CPF, o admin perde o unico
            // jeito de redefinir a propria senha (ver redefinirSenha).
            if (request.cpf() == null || request.cpf().isBlank()) {
                throw new CampoInvalidoException("cpf", "CPF e obrigatorio para usuarios com perfil ADMIN");
            }
            validarCpfUnico(request.cpf(), id);
            usuario.setCpf(request.cpf());
        }

        return usuarioRepository.save(usuario);
    }

    /**
     * Cadastro publico (aluno se autocadastrando na tela de login) - perfil
     * e sempre ALUNO, hardcoded aqui, nunca vindo do request (que nem tem
     * esse campo - ver CadastroAlunoRequest). Diferente de criar(), usa a
     * senha que a propria pessoa escolheu (nao uma provisoria).
     */
    public Usuario registrarAluno(CadastroAlunoRequest request) {
        validarEmailUnico(request.email(), null);
        validarRgmUnico(request.rgm(), null);

        Usuario usuario = Usuario.builder()
                .nome(request.nome())
                .email(request.email())
                .perfil(Perfil.ALUNO)
                .rgm(request.rgm())
                .curso(request.curso())
                .senha(passwordEncoder.encode(request.senha()))
                .build();

        return usuarioRepository.save(usuario);
    }

    /**
     * "Esqueci minha senha" - identidade confirmada com email + RGM (aluno)
     * ou CPF (admin), sem link por email (ver RedefinirSenhaRequest).
     * Tambem serve pro Admin (sempre cadastrado por outro Admin com senha
     * provisoria que ninguem sabe) definir a primeira senha de verdade.
     * A mensagem generica ("dados nao conferem") evita expor se o email
     * existe ou qual o motivo exato de nao bater.
     */
    public Usuario redefinirSenha(RedefinirSenhaRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow(() -> new CredenciaisInvalidasException(
                        "Dados nao conferem - verifique o email e o RGM/CPF informados"));

        String identificadorEsperado = usuario.getPerfil() == Perfil.ALUNO ? usuario.getRgm() : usuario.getCpf();

        if (identificadorEsperado == null || !identificadorEsperado.equals(request.identificador())) {
            throw new CredenciaisInvalidasException(
                    "Dados nao conferem - verifique o email e o RGM/CPF informados");
        }

        usuario.setSenha(passwordEncoder.encode(request.novaSenha()));
        return usuarioRepository.save(usuario);
    }

    public void excluir(UUID id, UUID solicitanteId) {
        Usuario usuario = buscarPorId(id);
        // Mesma razao da trava em atualizar(): apagar a propria conta
        // deixaria o sistema potencialmente sem administrador, e nao ha
        // como desfazer pela aplicacao.
        if (id.equals(solicitanteId)) {
            throw new RegraNegocioException("Voce nao pode excluir a propria conta.");
        }
        if (inscricaoRepository.existsByAlunoId(id)) {
            throw new RegraNegocioException("Nao e possivel excluir um usuario que ja possui inscricoes");
        }
        usuarioRepository.delete(usuario);
    }

    private void validarIdentificadoresObrigatorios(NovoUsuarioRequest request) {
        if (request.perfil() == Perfil.ALUNO && (request.rgm() == null || request.rgm().isBlank())) {
            throw new CampoInvalidoException("rgm", "RGM e obrigatorio para usuarios com perfil ALUNO");
        }
        // CPF e obrigatorio pro Admin - e o identificador usado no
        // "esqueci minha senha" (ver redefinirSenha); sem ele, um Admin
        // cadastrado por outro Admin nunca teria como definir a propria
        // senha, ja que a senha provisoria gerada no cadastro nunca e
        // revelada pra ninguem.
        if (request.perfil() == Perfil.ADMIN && (request.cpf() == null || request.cpf().isBlank())) {
            throw new CampoInvalidoException("cpf", "CPF e obrigatorio para usuarios com perfil ADMIN");
        }
    }

    /** ignorarId: ao atualizar, o proprio usuario nao deve contar como
     *  conflito consigo mesmo. */
    private void validarEmailUnico(String email, UUID ignorarId) {
        usuarioRepository.findByEmail(email)
                .filter(outro -> ignorarId == null || !outro.getId().equals(ignorarId))
                .ifPresent(outro -> {
                    throw new ConflitoException("Ja existe um usuario cadastrado com este email");
                });
    }

    private void validarRgmUnico(String rgm, UUID ignorarId) {
        if (rgm == null) {
            return;
        }
        usuarioRepository.findByRgm(rgm)
                .filter(outro -> ignorarId == null || !outro.getId().equals(ignorarId))
                .ifPresent(outro -> {
                    throw new ConflitoException("Ja existe um usuario cadastrado com este RGM");
                });
    }

    private void validarCpfUnico(String cpf, UUID ignorarId) {
        if (cpf == null) {
            return;
        }
        usuarioRepository.findByCpf(cpf)
                .filter(outro -> ignorarId == null || !outro.getId().equals(ignorarId))
                .ifPresent(outro -> {
                    throw new ConflitoException("Ja existe um usuario cadastrado com este CPF");
                });
    }
}

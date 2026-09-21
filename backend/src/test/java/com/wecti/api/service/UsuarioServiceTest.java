package com.wecti.api.service;

import com.wecti.api.domain.Perfil;
import com.wecti.api.domain.Usuario;
import com.wecti.api.dto.AtualizarPerfilRequest;
import com.wecti.api.dto.NovoUsuarioRequest;
import com.wecti.api.exception.CampoInvalidoException;
import com.wecti.api.exception.ConflitoException;
import com.wecti.api.exception.RegraNegocioException;
import com.wecti.api.repository.InscricaoRepository;
import com.wecti.api.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Edicao de cadastro - os dois caminhos, que tem permissoes diferentes:
 *
 * <ul>
 *   <li>{@code atualizarMeuPerfil} (PUT /usuarios/me): a propria pessoa,
 *       so nos campos que sao dela. Nunca mexe em perfil nem email.</li>
 *   <li>{@code atualizar} (PUT /usuarios/{id}): a tela de Usuarios do
 *       admin, que mexe em tudo - inclusive no perfil de outras pessoas,
 *       menos no proprio.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private InscricaoRepository inscricaoRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private UsuarioService usuarioService;

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID ALUNO_ID = UUID.randomUUID();

    private Usuario admin() {
        Usuario u = Usuario.builder().id(ADMIN_ID).nome("Admin").email("admin@unicid.test")
                .perfil(Perfil.ADMIN).cpf("12345678901").build();
        when(usuarioRepository.findById(ADMIN_ID)).thenReturn(Optional.of(u));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        return u;
    }

    private Usuario aluno() {
        Usuario u = Usuario.builder().id(ALUNO_ID).nome("Aluno").email("aluno@unicid.test")
                .perfil(Perfil.ALUNO).rgm("20000001").curso("ADS").build();
        when(usuarioRepository.findById(ALUNO_ID)).thenReturn(Optional.of(u));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        return u;
    }

    // ── PUT /usuarios/me ───────────────────────────────────────────────

    @Nested
    @DisplayName("a propria pessoa corrigindo o cadastro")
    class MeuPerfil {

        @Test
        @DisplayName("admin altera nome e CPF")
        void adminAlteraNomeECpf() {
            admin();

            Usuario r = usuarioService.atualizarMeuPerfil(ADMIN_ID,
                    new AtualizarPerfilRequest("Admin Corrigido", null, "98765432100", null));

            assertThat(r.getNome()).isEqualTo("Admin Corrigido");
            assertThat(r.getCpf()).isEqualTo("98765432100");
        }

        @Test
        @DisplayName("admin sem CPF e recusado - e o unico jeito de ele redefinir a propria senha")
        void adminPrecisaDeCpf() {
            admin();

            assertThatThrownBy(() -> usuarioService.atualizarMeuPerfil(ADMIN_ID,
                    new AtualizarPerfilRequest("Admin", null, null, null)))
                    .isInstanceOf(CampoInvalidoException.class);
        }

        @Test
        @DisplayName("CPF ja usado por outro e recusado")
        void cpfDuplicadoRecusado() {
            admin();
            when(usuarioRepository.findByCpf("99999999999"))
                    .thenReturn(Optional.of(Usuario.builder().id(UUID.randomUUID()).build()));

            assertThatThrownBy(() -> usuarioService.atualizarMeuPerfil(ADMIN_ID,
                    new AtualizarPerfilRequest("Admin", null, "99999999999", null)))
                    .isInstanceOf(ConflitoException.class);
        }

        @Test
        @DisplayName("manter o proprio CPF nao conta como duplicado")
        void proprioCpfNaoEhConflito() {
            Usuario u = admin();
            when(usuarioRepository.findByCpf("12345678901")).thenReturn(Optional.of(u));

            assertThatCode(() -> usuarioService.atualizarMeuPerfil(ADMIN_ID,
                    new AtualizarPerfilRequest("Admin", null, "12345678901", null)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("o campo do outro perfil e ignorado: aluno nao ganha CPF")
        void alunoNaoGanhaCpf() {
            aluno();

            Usuario r = usuarioService.atualizarMeuPerfil(ALUNO_ID,
                    new AtualizarPerfilRequest("Aluno", "20000009", "11111111111", "Engenharia"));

            assertThat(r.getRgm()).isEqualTo("20000009");
            assertThat(r.getCpf()).as("CPF e campo de admin - nao pode grudar num aluno").isNull();
        }

        @Test
        @DisplayName("o campo do outro perfil e ignorado: admin nao ganha RGM nem curso")
        void adminNaoGanhaRgm() {
            admin();

            Usuario r = usuarioService.atualizarMeuPerfil(ADMIN_ID,
                    new AtualizarPerfilRequest("Admin", "20000002", "12345678901", "ADS"));

            assertThat(r.getRgm()).isNull();
            assertThat(r.getCurso()).isNull();
        }

        @Test
        @DisplayName("nem o aluno nem o admin mudam o proprio perfil ou email por aqui")
        void perfilEEmailIntocados() {
            Usuario u = aluno();

            Usuario r = usuarioService.atualizarMeuPerfil(ALUNO_ID,
                    new AtualizarPerfilRequest("Aluno", "20000009", null, "ADS"));

            assertThat(r.getPerfil())
                    .as("se o perfil viesse do corpo, qualquer aluno viraria admin")
                    .isEqualTo(Perfil.ALUNO);
            assertThat(r.getEmail()).isEqualTo(u.getEmail());
        }
    }

    // ── PUT /usuarios/{id} - tela de Usuarios ──────────────────────────

    @Nested
    @DisplayName("admin editando cadastros pela tela de Usuarios")
    class TelaDeUsuarios {

        private NovoUsuarioRequest comoAluno(String nome) {
            return new NovoUsuarioRequest(nome, "aluno@unicid.test", Perfil.ALUNO, "20000001", null, "ADS");
        }

        private NovoUsuarioRequest comoAdmin(String nome) {
            return new NovoUsuarioRequest(nome, "admin@unicid.test", Perfil.ADMIN, null, "12345678901", null);
        }

        @Test
        @DisplayName("admin edita o cadastro de um aluno")
        void editaAluno() {
            aluno();

            Usuario r = usuarioService.atualizar(ALUNO_ID, comoAluno("Nome Corrigido"), ADMIN_ID);

            assertThat(r.getNome()).isEqualTo("Nome Corrigido");
        }

        @Test
        @DisplayName("admin edita o proprio cadastro, desde que continue admin")
        void editaSiMesmoComoAdmin() {
            admin();

            Usuario r = usuarioService.atualizar(ADMIN_ID, comoAdmin("Admin Renomeado"), ADMIN_ID);

            assertThat(r.getNome()).isEqualTo("Admin Renomeado");
            assertThat(r.getPerfil()).isEqualTo(Perfil.ADMIN);
        }

        @Test
        @DisplayName("admin NAO rebaixa a si mesmo para aluno")
        void naoSeRebaixa() {
            admin();

            assertThatThrownBy(() -> usuarioService.atualizar(ADMIN_ID, comoAluno("Admin"), ADMIN_ID))
                    .as("so admin cria admin: se ele se rebaixasse, nao sobraria ninguem "
                            + "para promove-lo de volta e o sistema ficaria sem administrador")
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("proprio acesso");

            verify(usuarioRepository, never()).save(any(Usuario.class));
        }

        @Test
        @DisplayName("admin PODE rebaixar outro admin - sempre sobra ele mesmo")
        void rebaixaOutroAdmin() {
            Usuario outro = Usuario.builder().id(ALUNO_ID).nome("Outro Admin")
                    .email("outro@unicid.test").perfil(Perfil.ADMIN).cpf("11111111111").build();
            when(usuarioRepository.findById(ALUNO_ID)).thenReturn(Optional.of(outro));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

            Usuario r = usuarioService.atualizar(ALUNO_ID, comoAluno("Outro Admin"), ADMIN_ID);

            assertThat(r.getPerfil()).isEqualTo(Perfil.ALUNO);
        }

        @Test
        @DisplayName("admin NAO exclui a propria conta")
        void naoSeExclui() {
            admin();

            assertThatThrownBy(() -> usuarioService.excluir(ADMIN_ID, ADMIN_ID))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("propria conta");

            verify(usuarioRepository, never()).delete(any(Usuario.class));
        }

        @Test
        @DisplayName("admin exclui outro usuario sem inscricoes")
        void excluiOutro() {
            aluno();
            when(inscricaoRepository.existsByAlunoId(ALUNO_ID)).thenReturn(false);

            usuarioService.excluir(ALUNO_ID, ADMIN_ID);

            verify(usuarioRepository).delete(any(Usuario.class));
        }

        @Test
        @DisplayName("usuario com inscricoes continua protegido contra exclusao")
        void naoExcluiComInscricoes() {
            aluno();
            when(inscricaoRepository.existsByAlunoId(ALUNO_ID)).thenReturn(true);

            assertThatThrownBy(() -> usuarioService.excluir(ALUNO_ID, ADMIN_ID))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("inscricoes");
        }
    }
}

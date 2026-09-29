package com.wecti.api.service;

import com.wecti.api.domain.Perfil;
import com.wecti.api.domain.Usuario;
import com.wecti.api.dto.CadastroAlunoRequest;
import com.wecti.api.dto.LoginRequest;
import com.wecti.api.dto.RedefinirSenhaRequest;
import com.wecti.api.exception.CredenciaisInvalidasException;
import com.wecti.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "Esqueci minha senha" com os dados digitados como um aluno digita de
 * verdade - no celular, com o teclado colando espaco e a autocorrecao
 * mudando maiuscula.
 *
 * <p>Nasceu do 2o dia da WECTI 2026 (29/09): depois de corrigido o
 * bloqueio por IP, ainda sobraram alunos que "trocavam a senha e nao
 * entrava". A conta de identidade comparava as strings cruas
 * ({@code equals}), entao um unico espaco invisivel no fim do RGM
 * devolvia "Dados nao conferem" - e o aluno, que tinha digitado o RGM
 * certo, nao tinha como adivinhar o que estava errado.
 */
@SpringBootTest
@ActiveProfiles("test")
class RecuperacaoDeSenhaTest {

    private static final String EMAIL = "cenario@unicid.test";
    private static final String RGM = "20000010";
    private static final String SENHA_ORIGINAL = "senhaoriginal";

    @Autowired private AuthService authService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void limparECadastrar() {
        usuarioRepository.deleteAll();
        usuarioService.registrarAluno(
                new CadastroAlunoRequest("Aluno Cenario", EMAIL, SENHA_ORIGINAL, RGM, "ADS"));
    }

    private void redefinir(String email, String identificador, String novaSenha) {
        authService.redefinirSenha(new RedefinirSenhaRequest(email, identificador, novaSenha));
    }

    @Nested
    @DisplayName("o RGM digitado no celular")
    class IdentificadorTolerante {

        @Test
        @DisplayName("o RGM exato redefine (controle)")
        void rgmExato() {
            assertThatCode(() -> redefinir(EMAIL, RGM, "senhanova123"))
                    .doesNotThrowAnyException();
        }

        /** O caso classico: o teclado do celular cola um espaco depois do
         *  numero e o aluno nao ve. */
        @Test
        @DisplayName("aceita espaco sobrando depois do RGM")
        void rgmComEspacoAtras() {
            assertThatCode(() -> redefinir(EMAIL, RGM + " ", "senhanova123"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("aceita espaco sobrando antes do RGM")
        void rgmComEspacoNaFrente() {
            assertThatCode(() -> redefinir(EMAIL, " " + RGM, "senhanova123"))
                    .doesNotThrowAnyException();
        }

        /** Tolerar espaco nao pode virar tolerar RGM errado: o
         *  identificador e a unica prova de identidade deste fluxo. */
        @Test
        @DisplayName("continua recusando um RGM que nao e o do aluno")
        void rgmDeOutraPessoa() {
            assertThatThrownBy(() -> redefinir(EMAIL, "20000099", "senhanova123"))
                    .isInstanceOf(CredenciaisInvalidasException.class);
        }

        /** Os digitos sao os mesmos - so a pontuacao sobra. Reprovar isso
         *  nao protege nada, so trava quem digitou o numero certo. */
        @Test
        @DisplayName("aceita RGM digitado com pontuacao")
        void rgmComPontuacao() {
            assertThatCode(() -> redefinir(EMAIL, "2000.0010", "senhanova123"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("recusa identificador sem digito nenhum")
        void identificadorVazio() {
            assertThatThrownBy(() -> redefinir(EMAIL, "  -  ", "senhanova123"))
                    .isInstanceOf(CredenciaisInvalidasException.class);
        }
    }

    /** O admin recupera a senha pelo CPF, e ninguem digita CPF sem ponto
     *  e traco quando le de um documento. */
    @Test
    @DisplayName("admin redefine com o CPF mascarado")
    void cpfComMascara() {
        usuarioRepository.save(Usuario.builder()
                .nome("Admin Cenario")
                .email("admin.cenario@unicid.test")
                .perfil(Perfil.ADMIN)
                .cpf("12345678901")
                .senha(passwordEncoder.encode("provisoria"))
                .build());

        assertThatCode(() -> redefinir("admin.cenario@unicid.test", "123.456.789-01", "senhanova123"))
                .doesNotThrowAnyException();
    }

    @Nested
    @DisplayName("o email digitado no celular")
    class EmailTolerante {

        /** A autocorrecao do celular maiuscula a primeira letra. Em
         *  producao o MySQL ja perdoa isso (utf8mb4_unicode_ci), mas o
         *  H2 deste teste nao - o que prova que a tolerancia tem de
         *  estar no codigo, e nao so na collation do banco. */
        @Test
        @DisplayName("aceita a primeira letra maiuscula")
        void emailComMaiuscula() {
            String comMaiuscula = Character.toUpperCase(EMAIL.charAt(0)) + EMAIL.substring(1);
            assertThatCode(() -> redefinir(comMaiuscula, RGM, "senhanova123"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("aceita espaco sobrando no email")
        void emailComEspaco() {
            assertThatCode(() -> redefinir(" " + EMAIL + " ", RGM, "senhanova123"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("depois de redefinir")
    class DepoisDeRedefinir {

        @Test
        @DisplayName("a senha nova entra e a antiga para de valer")
        void senhaNovaVale() {
            redefinir(EMAIL, RGM, "senhafinal123");

            assertThatCode(() -> authService.login(new LoginRequest(EMAIL, "senhafinal123")))
                    .as("o aluno entra com a senha que acabou de definir")
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, SENHA_ORIGINAL)))
                    .as("a senha antiga nao vale mais")
                    .isInstanceOf(CredenciaisInvalidasException.class);
        }

        /** O login tem de perdoar o mesmo que a redefinicao perdoa: senao
         *  o aluno redefine com sucesso e trava na tela seguinte. */
        @Test
        @DisplayName("o login aceita o email com espaco e maiuscula")
        void loginTolerante() {
            redefinir(EMAIL, RGM, "senhafinal123");

            assertThatCode(() -> authService.login(
                    new LoginRequest("  " + EMAIL.toUpperCase() + " ", "senhafinal123")))
                    .doesNotThrowAnyException();
        }

        /** A senha, ao contrario do email, e comparada byte a byte de
         *  proposito - espaco em senha e caractere valido. */
        @Test
        @DisplayName("a senha continua sensivel a espaco e maiuscula")
        void senhaNaoENormalizada() {
            redefinir(EMAIL, RGM, "senhafinal123");

            assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "senhafinal123 ")))
                    .isInstanceOf(CredenciaisInvalidasException.class);
            assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "SenhaFinal123")))
                    .isInstanceOf(CredenciaisInvalidasException.class);
        }
    }

    /**
     * Aluno sem RGM no cadastro. Nao deveria existir (o RGM e obrigatorio
     * para ALUNO), mas se existir no banco o identificador esperado e
     * {@code null} - e ai nenhum valor digitado pode dar certo. O aluno
     * fica sem nenhum caminho de recuperacao pela aplicacao, e precisa do
     * admin. O teste garante ao menos que isso recusa em vez de estourar
     * NullPointerException.
     */
    @Test
    @DisplayName("aluno sem RGM recusa a redefinicao sem quebrar")
    void alunoSemRgm() {
        usuarioRepository.save(Usuario.builder()
                .nome("Aluno Sem RGM")
                .email("semrgm@unicid.test")
                .perfil(Perfil.ALUNO)
                .senha(passwordEncoder.encode("provisoria"))
                .build());

        assertThatThrownBy(() -> redefinir("semrgm@unicid.test", "20000055", "senhanova123"))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }
}

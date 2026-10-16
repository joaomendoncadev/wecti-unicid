package com.wecti.api.service;

import com.wecti.api.domain.Disciplina;
import com.wecti.api.domain.DistribuicaoPontuacao;
import com.wecti.api.domain.Perfil;
import com.wecti.api.domain.TurnoDisciplina;
import com.wecti.api.domain.Usuario;
import com.wecti.api.dto.ItemDistribuicaoRequest;
import com.wecti.api.dto.PontuacaoResponse;
import com.wecti.api.exception.CampoInvalidoException;
import com.wecti.api.exception.RecursoNaoEncontradoException;
import com.wecti.api.exception.RegraNegocioException;
import com.wecti.api.repository.DisciplinaRepository;
import com.wecti.api.repository.DistribuicaoPontuacaoRepository;
import com.wecti.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
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
 * O aluno repartindo a pontuacao do WECTI entre as turmas.
 *
 * <p>O que estes testes protegem e a nota de outras pessoas: um erro aqui
 * nao aparece na tela, aparece no fim do semestre, quando o professor
 * lanca decimo a mais ou a menos para alguem.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DistribuicaoPontuacaoServiceTest {

    private static final UUID ALUNO_ID = UUID.randomUUID();
    private static final UUID TURMA_A = UUID.randomUUID();
    private static final UUID TURMA_B = UUID.randomUUID();

    @Mock private DistribuicaoPontuacaoRepository repository;
    @Mock private DisciplinaRepository disciplinaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PontuacaoService pontuacaoService;

    private DistribuicaoPontuacaoService service;
    private Usuario aluno;

    @BeforeEach
    void preparar() {
        // RegraDistribuicao de verdade, com os valores de producao: e a
        // regra que decide limite e nota, mocka-la esvaziaria os testes.
        service = new DistribuicaoPontuacaoService(repository, disciplinaRepository,
                usuarioRepository, pontuacaoService, new RegraDistribuicao(1000, 2000));

        aluno = Usuario.builder().id(ALUNO_ID).nome("Aluno").perfil(Perfil.ALUNO).rgm("20000001").build();
        when(usuarioRepository.findById(ALUNO_ID)).thenReturn(Optional.of(aluno));
        when(disciplinaRepository.findAllById(any())).thenAnswer(chamada -> {
            Iterable<UUID> ids = chamada.getArgument(0);
            List<Disciplina> achadas = new java.util.ArrayList<>();
            for (UUID id : ids) {
                if (id.equals(TURMA_A)) achadas.add(turma(TURMA_A, "14362", "ALGORITMOS"));
                if (id.equals(TURMA_B)) achadas.add(turma(TURMA_B, "14365", "MODELAGEM"));
            }
            return achadas;
        });
        when(repository.saveAll(any())).thenAnswer(c -> c.getArgument(0));
        when(repository.findByAlunoId(ALUNO_ID)).thenReturn(List.of());
    }

    private Disciplina turma(UUID id, String codigo, String nome) {
        return Disciplina.builder().id(id).codigo(codigo).nome(nome)
                .turno(TurnoDisciplina.NOITE).diaSemana("TERÇA-FEIRA")
                .professor("Professor Teste").build();
    }

    private void pontuacaoDoAluno(int total) {
        when(pontuacaoService.calcular(ALUNO_ID))
                .thenReturn(new PontuacaoResponse(total, total, 0, 2000, 0, List.of(), List.of()));
    }

    private void salvar(ItemDistribuicaoRequest... itens) {
        service.salvar(ALUNO_ID, List.of(itens));
    }

    @Nested
    @DisplayName("o limite do aluno")
    class Limite {

        @Test
        @DisplayName("quem tem 1300 distribui 1300, repartidos como quiser")
        void abaixoDoTeto() {
            pontuacaoDoAluno(1300);

            assertThat(service.limiteDe(ALUNO_ID)).isEqualTo(1300);
            assertThatCode(() -> salvar(
                    new ItemDistribuicaoRequest(TURMA_A, 800),
                    new ItemDistribuicaoRequest(TURMA_B, 500)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("quem tem 2300 so pode distribuir 2000")
        void acimaDoTeto() {
            pontuacaoDoAluno(2300);

            assertThat(service.limiteDe(ALUNO_ID)).isEqualTo(2000);
            assertThatThrownBy(() -> salvar(new ItemDistribuicaoRequest(TURMA_A, 2300)))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("2000");
        }

        /** A regra central: nao pode distribuir mais do que tem. */
        @Test
        @DisplayName("recusa passar do limite, e diz quanto sobrou")
        void naoPassaDoLimite() {
            pontuacaoDoAluno(1300);

            assertThatThrownBy(() -> salvar(
                    new ItemDistribuicaoRequest(TURMA_A, 1000),
                    new ItemDistribuicaoRequest(TURMA_B, 500)))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("Reduza 200");

            verify(repository, never()).saveAll(any());
        }

        @Test
        @DisplayName("exatamente no limite passa")
        void exatamenteNoLimite() {
            pontuacaoDoAluno(2000);

            assertThatCode(() -> salvar(
                    new ItemDistribuicaoRequest(TURMA_A, 1500),
                    new ItemDistribuicaoRequest(TURMA_B, 500)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("o que a tela recebe de volta")
    class Resposta {

        @Test
        @DisplayName("incompleta informa o que falta e nao libera o comprovante")
        void incompleta() {
            pontuacaoDoAluno(2000);
            when(repository.findByAlunoId(ALUNO_ID)).thenReturn(List.of(
                    DistribuicaoPontuacao.builder().aluno(aluno)
                            .disciplina(turma(TURMA_A, "14362", "ALGORITMOS")).pontos(1500).build()));

            var resposta = service.responderDoAluno(ALUNO_ID);

            assertThat(resposta.limite()).isEqualTo(2000);
            assertThat(resposta.distribuido()).isEqualTo(1500);
            assertThat(resposta.restante()).isEqualTo(500);
            assertThat(resposta.completa()).isFalse();
            assertThat(resposta.notaTotal()).isEqualByComparingTo(new BigDecimal("1.5"));
        }

        @Test
        @DisplayName("fechada em zero libera o comprovante")
        void completa() {
            pontuacaoDoAluno(2000);
            when(repository.findByAlunoId(ALUNO_ID)).thenReturn(List.of(
                    DistribuicaoPontuacao.builder().aluno(aluno)
                            .disciplina(turma(TURMA_A, "14362", "ALGORITMOS")).pontos(2000).build()));

            var resposta = service.responderDoAluno(ALUNO_ID);

            assertThat(resposta.restante()).isZero();
            assertThat(resposta.completa()).isTrue();
            assertThat(resposta.notaTotal()).isEqualByComparingTo(new BigDecimal("2"));
        }

        /** Aluno sem pontuacao nenhuma nao tem distribuicao "completa" -
         *  senao a tela ofereceria comprovante de nada. */
        @Test
        @DisplayName("aluno sem pontuacao nao conta como completa")
        void semPontuacao() {
            pontuacaoDoAluno(0);

            var resposta = service.responderDoAluno(ALUNO_ID);

            assertThat(resposta.limite()).isZero();
            assertThat(resposta.completa()).isFalse();
        }
    }

    @Nested
    @DisplayName("as travas de integridade")
    class Travas {

        @Test
        @DisplayName("a mesma turma duas vezes e recusada")
        void turmaRepetida() {
            pontuacaoDoAluno(2000);

            assertThatThrownBy(() -> salvar(
                    new ItemDistribuicaoRequest(TURMA_A, 500),
                    new ItemDistribuicaoRequest(TURMA_A, 300)))
                    .isInstanceOf(CampoInvalidoException.class);
        }

        @Test
        @DisplayName("turma que nao existe e recusada")
        void turmaInexistente() {
            pontuacaoDoAluno(2000);

            assertThatThrownBy(() -> salvar(new ItemDistribuicaoRequest(UUID.randomUUID(), 500)))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
        }

        /** Linha zerada e como o aluno tira os pontos de uma materia: ela
         *  simplesmente nao e gravada, em vez de virar erro. */
        @Test
        @DisplayName("linha com zero e descartada, nao vira erro")
        void linhaZerada() {
            pontuacaoDoAluno(2000);

            assertThatCode(() -> salvar(
                    new ItemDistribuicaoRequest(TURMA_A, 500),
                    new ItemDistribuicaoRequest(TURMA_B, 0)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("admin nao distribui pontuacao")
        void adminNaoDistribui() {
            aluno.setPerfil(Perfil.ADMIN);
            pontuacaoDoAluno(2000);

            assertThatThrownBy(() -> salvar(new ItemDistribuicaoRequest(TURMA_A, 500)))
                    .isInstanceOf(CampoInvalidoException.class);
        }

        /** Salvar substitui tudo: turma que sumiu da lista foi turma que
         *  o aluno zerou. Sem o delete antes, o valor antigo ficaria. */
        @Test
        @DisplayName("salvar substitui a distribuicao inteira")
        void substituiTudo() {
            pontuacaoDoAluno(2000);

            salvar(new ItemDistribuicaoRequest(TURMA_A, 500));

            verify(repository).deleteByAlunoId(ALUNO_ID);
            verify(repository).saveAll(any());
        }
    }
}

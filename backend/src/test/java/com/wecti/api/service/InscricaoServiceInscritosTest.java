package com.wecti.api.service;

import com.wecti.api.domain.InscricaoStatus;
import com.wecti.api.dto.InscritoEventoResponse;
import com.wecti.api.exception.RecursoNaoEncontradoException;
import com.wecti.api.repository.CheckinRepository;
import com.wecti.api.repository.EventoRepository;
import com.wecti.api.repository.InscricaoRepository;
import com.wecti.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lista de contato dos inscritos num evento (GET /eventos/{id}/inscritos).
 *
 * <p>O que estes testes protegem, em ordem de importancia:
 *
 * <ol>
 *   <li><b>Quem cancelou nao aparece.</b> E o erro que passaria despercebido
 *       em desenvolvimento - com o banco de teste cheio de inscricao ATIVA, a
 *       lista parece certa mesmo se o filtro de status sumir. Em producao
 *       vira e-mail para quem desistiu da palestra.</li>
 *   <li><b>Evento inexistente e 404, nao lista vazia.</b> Sem isso, um UUID
 *       errado devolve 200 com [] e o admin conclui que ninguem se inscreveu.</li>
 *   <li><b>Nome e e-mail chegam preenchidos</b> - o motivo do endpoint existir.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InscricaoServiceInscritosTest {

    private static final UUID EVENTO_ID = UUID.randomUUID();

    @Mock private InscricaoRepository inscricaoRepository;
    @Mock private EventoRepository eventoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private CheckinRepository checkinRepository;

    private InscricaoService inscricaoService;

    @BeforeEach
    void preparar() {
        inscricaoService = new InscricaoService(inscricaoRepository, eventoRepository,
                usuarioRepository, checkinRepository, new PrazoInscricao(15));
        when(eventoRepository.existsById(EVENTO_ID)).thenReturn(true);
    }

    @Test
    @DisplayName("devolve nome e e-mail de cada aluno inscrito")
    void devolveNomeEEmail() {
        when(inscricaoRepository.findInscritosDoEvento(EVENTO_ID, InscricaoStatus.ATIVA))
                .thenReturn(List.of(
                        inscritoDe("Ana Souza", "ana@aluno.unicid.edu.br", "12345678"),
                        inscritoDe("Bruno Lima", "bruno@aluno.unicid.edu.br", "87654321")));

        var inscritos = inscricaoService.listarInscritosDoEvento(EVENTO_ID);

        assertThat(inscritos).hasSize(2);
        assertThat(inscritos).extracting("nome", "email")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Ana Souza", "ana@aluno.unicid.edu.br"),
                        org.assertj.core.groups.Tuple.tuple("Bruno Lima", "bruno@aluno.unicid.edu.br"));
        assertThat(inscritos.get(0).rgm()).isEqualTo("12345678");
        assertThat(inscritos.get(0).alunoId()).isNotNull();
        assertThat(inscritos.get(0).inscritoEm()).isNotNull();
    }

    @Test
    @DisplayName("pede ao banco so as inscricoes ATIVAS - quem cancelou saiu da turma")
    void consultaApenasAtivas() {
        when(inscricaoRepository.findInscritosDoEvento(EVENTO_ID, InscricaoStatus.ATIVA))
                .thenReturn(List.of());

        inscricaoService.listarInscritosDoEvento(EVENTO_ID);

        // O filtro precisa chegar ao banco. Se alguem trocar a consulta por
        // findByEventoId e filtrar depois, cancelados entrariam na lista.
        verify(inscricaoRepository).findInscritosDoEvento(EVENTO_ID, InscricaoStatus.ATIVA);
        verify(inscricaoRepository, never()).findByEventoId(eq(EVENTO_ID));
    }

    @Test
    @DisplayName("evento inexistente e 404, nao lista vazia")
    void eventoInexistenteEstoura() {
        UUID desconhecido = UUID.randomUUID();
        when(eventoRepository.existsById(desconhecido)).thenReturn(false);

        assertThatThrownBy(() -> inscricaoService.listarInscritosDoEvento(desconhecido))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining(desconhecido.toString());

        verify(inscricaoRepository, never()).findInscritosDoEvento(eq(desconhecido), eq(InscricaoStatus.ATIVA));
    }

    @Test
    @DisplayName("evento existente sem inscritos devolve lista vazia, nao erro")
    void eventoSemInscritos() {
        when(inscricaoRepository.findInscritosDoEvento(EVENTO_ID, InscricaoStatus.ATIVA))
                .thenReturn(List.of());

        assertThat(inscricaoService.listarInscritosDoEvento(EVENTO_ID)).isEmpty();
    }

    private InscritoEventoResponse inscritoDe(String nome, String email, String rgm) {
        return new InscritoEventoResponse(UUID.randomUUID(), nome, email, rgm,
                LocalDateTime.now().minusDays(1));
    }
}

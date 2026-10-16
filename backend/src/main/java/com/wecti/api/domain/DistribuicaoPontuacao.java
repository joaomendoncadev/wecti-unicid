package com.wecti.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Quanto da pontuacao do WECTI o aluno jogou numa turma.
 *
 * <p><b>Guarda PONTOS, nao nota.</b> A nota sai da conversao na leitura
 * (ver {@link com.wecti.api.service.RegraDistribuicao}). Gravar a nota
 * convertida criaria duas fontes de verdade para o mesmo numero: se a
 * conversao mudasse, as linhas antigas passariam a mentir, e ninguem
 * saberia quais recalcular.
 */
@Entity
@Table(name = "distribuicoes_pontuacao", uniqueConstraints = @UniqueConstraint(
        columnNames = {"aluno_id", "disciplina_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistribuicaoPontuacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "aluno_id", nullable = false)
    private Usuario aluno;

    @ManyToOne(optional = false)
    @JoinColumn(name = "disciplina_id", nullable = false)
    private Disciplina disciplina;

    /** Sempre positivo: linha com zero nao e guardada, e removida. */
    @Column(nullable = false)
    private Integer pontos;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = LocalDateTime.now();
    }
}

package com.wecti.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "eventos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(length = 200)
    private String local;

    @Column(name = "data_hora_inicio", nullable = false)
    private LocalDateTime dataHoraInicio;

    @Column(name = "data_hora_fim", nullable = false)
    private LocalDateTime dataHoraFim;

    /**
     * Pontos que o evento vale para o aluno que fizer check-in E
     * check-out. Nao ha exigencia de tempo minimo de permanencia
     * (removida em setembro de 2026).
     *
     * <p>A penalidade de no-show NAO sai daqui: e um valor fixo,
     * configurado em app.pontuacao.penalidade-no-show e hoje igual a
     * zero (ver RegraPontuacao).
     */
    @Column(nullable = false)
    private Integer pontos;

    /**
     * Numero maximo de inscricoes ATIVAS. {@code null} significa sem
     * limite - e o que descreve os eventos criados antes desta regra
     * existir, e continua util para evento sem restricao de espaco.
     *
     * <p>So conta inscricao ativa: quem cancela devolve a vaga para a
     * fila. Ver InscricaoService.inscrever.
     */
    @Column
    private Integer capacidade;

    // O prazo de inscricao/cancelamento NAO fica aqui: depende de
    // configuracao (a folga para quem chega atrasado) e vive em
    // PrazoInscricao. Abaixo ficam so fatos de tempo do proprio evento.

    /** Ja comecou e ainda nao terminou. */
    @Transient
    public boolean isEmAndamento() {
        LocalDateTime agora = LocalDateTime.now();
        return !agora.isBefore(dataHoraInicio) && agora.isBefore(dataHoraFim);
    }

    @Transient
    public boolean isEncerrado() {
        return !LocalDateTime.now().isBefore(dataHoraFim);
    }

    /**
     * EAGER de proposito: o controller mapeia Evento -> EventoResponse
     * fora de uma transacao (open-in-view esta desligado), e @ManyToMany
     * e LAZY por padrao no Hibernate - acessar essa colecao depois que a
     * sessao ja fechou (ex.: ao listar eventos) derrubava a request com
     * LazyInitializationException. Sao poucos palestrantes por evento, o
     * custo de sempre carregar junto e desprezivel.
     */
    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "evento_palestrante",
            joinColumns = @JoinColumn(name = "evento_id"),
            inverseJoinColumns = @JoinColumn(name = "palestrante_id")
    )
    private Set<Palestrante> palestrantes = new HashSet<>();
}

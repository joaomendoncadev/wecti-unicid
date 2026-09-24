package com.wecti.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "checkins")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Checkin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "inscricao_id", nullable = false, unique = true)
    private Inscricao inscricao;

    @Column(nullable = false)
    private LocalDateTime entrada;

    private LocalDateTime saida;

    /**
     * Regra do certificado/pontuacao: <b>os dois QR codes lidos</b>.
     * Quem marcou a entrada e a saida cumpriu o criterio; nao ha exigencia
     * de tempo minimo de permanencia. Calculado em runtime, nao
     * persistido como coluna.
     *
     * <p>Como {@code entrada} e obrigatoria (a saida so e registrada em
     * cima de um check-in existente - ver CheckinService), o unico teste
     * que sobra e se houve check-out.
     *
     * <p><b>Ate setembro de 2026 exigia tambem permanencia >= 75% da
     * duracao do evento</b>, contada pela intersecao entre
     * [entrada, saida] e o horario do evento. O professor tirou a regra:
     * a presenca passou a ser comprovada so pela leitura dos dois QRs.
     * O percentual continua calculado e exibido na lista de presenca
     * (ver CheckinResponse.percentualPresenca), mas agora e so
     * informativo para o admin - nao decide pontuacao nem certificado.
     */
    @Transient
    public boolean isPresencaQualificada() {
        return saida != null;
    }
}

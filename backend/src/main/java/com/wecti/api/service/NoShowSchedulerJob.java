package com.wecti.api.service;

import com.wecti.api.domain.Evento;
import com.wecti.api.domain.Inscricao;
import com.wecti.api.domain.InscricaoStatus;
import com.wecti.api.repository.CheckinRepository;
import com.wecti.api.repository.EventoRepository;
import com.wecti.api.repository.InscricaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A pontuacao (PontuacaoService) ja calcula no-shows dinamicamente a cada
 * consulta, entao este job nao precisa persistir nada para a regra
 * funcionar - ele so registra a penalizacao no log logo apos o evento
 * encerrar, como pedido no escopo da Fase 1 (auditoria/observabilidade).
 * Roda periodicamente e olha uma janela recente para nao reprocessar o
 * historico inteiro a cada execucao.
 */
@Component
public class NoShowSchedulerJob {

    private static final Logger log = LoggerFactory.getLogger(NoShowSchedulerJob.class);
    private static final long JANELA_MINUTOS = 60;

    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final CheckinRepository checkinRepository;
    private final RegraPontuacao regraPontuacao;

    public NoShowSchedulerJob(EventoRepository eventoRepository, InscricaoRepository inscricaoRepository,
                               CheckinRepository checkinRepository, RegraPontuacao regraPontuacao) {
        this.eventoRepository = eventoRepository;
        this.inscricaoRepository = inscricaoRepository;
        this.checkinRepository = checkinRepository;
        this.regraPontuacao = regraPontuacao;
    }

    @Scheduled(fixedRateString = "${wecti.no-show-job.fixed-rate-ms:900000}")
    public void penalizarNoShows() {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime inicioJanela = agora.minusMinutes(JANELA_MINUTOS);

        List<Evento> encerradosRecentemente = eventoRepository.findByDataHoraFimBefore(agora).stream()
                .filter(evento -> evento.getDataHoraFim().isAfter(inicioJanela))
                .toList();

        for (Evento evento : encerradosRecentemente) {
            for (Inscricao inscricao : inscricaoRepository.findByEventoId(evento.getId())) {
                if (inscricao.getStatus() != InscricaoStatus.ATIVA) {
                    continue;
                }
                if (checkinRepository.findByInscricaoId(inscricao.getId()).isPresent()) {
                    continue;
                }
                // A penalidade hoje e zero, entao dizer "perdeu 0 pontos"
                // seria ruido. O registro continua util - e o rastro de
                // quem reservou vaga e nao apareceu - e o valor volta a
                // aparecer se a penalidade for reativada na configuracao.
                int penalidade = regraPontuacao.penalidadeNoShow();
                if (penalidade == 0) {
                    log.warn("No-show: aluno {} nao compareceu ao evento '{}' ({})",
                            inscricao.getAluno().getEmail(), evento.getTitulo(), evento.getId());
                } else {
                    log.warn("No-show: aluno {} perdeu {} pontos no evento '{}' ({})",
                            inscricao.getAluno().getEmail(), penalidade,
                            evento.getTitulo(), evento.getId());
                }
            }
        }
    }
}

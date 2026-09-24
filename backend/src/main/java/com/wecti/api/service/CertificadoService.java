package com.wecti.api.service;

import com.wecti.api.domain.Certificado;
import com.wecti.api.domain.Checkin;
import com.wecti.api.domain.Evento;
import com.wecti.api.domain.Inscricao;
import com.wecti.api.dto.CertificadoValidacaoResponse;
import com.wecti.api.exception.RecursoNaoEncontradoException;
import com.wecti.api.exception.RegraNegocioException;
import com.wecti.api.repository.CertificadoRepository;
import com.wecti.api.repository.CheckinRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class CertificadoService {

    private final CheckinRepository checkinRepository;
    private final CertificadoRepository certificadoRepository;
    private final CodigoCertificadoGenerator codigoGenerator;
    private final CertificadoPdfRenderer pdfRenderer;
    private final QrCodeService qrCodeService;

    public CertificadoService(CheckinRepository checkinRepository, CertificadoRepository certificadoRepository,
                               CodigoCertificadoGenerator codigoGenerator, CertificadoPdfRenderer pdfRenderer,
                               QrCodeService qrCodeService) {
        this.checkinRepository = checkinRepository;
        this.certificadoRepository = certificadoRepository;
        this.codigoGenerator = codigoGenerator;
        this.pdfRenderer = pdfRenderer;
        this.qrCodeService = qrCodeService;
    }

    /**
     * @param baseUrl endereco publico do site, usado pra montar o link do
     *                QR code e o texto de validacao. Vem do Origin da
     *                requisicao (ver CertificadoController) pra o QR
     *                apontar pro mesmo endereco que o aluno esta usando,
     *                em vez de um "localhost" fixo que so funcionaria na
     *                maquina do servidor.
     */
    public byte[] emitir(Inscricao inscricao, String baseUrl) {
        Evento evento = inscricao.getEvento();
        Checkin checkin = checkinRepository.findByInscricaoId(inscricao.getId())
                .orElseThrow(() -> new RegraNegocioException(
                        "Criterios de presenca ainda nao cumpridos (check-in/check-out pendentes)"));

        if (!checkin.isPresencaQualificada()) {
            throw new RegraNegocioException(
                    "Criterios de presenca ainda nao cumpridos (falta o check-out)");
        }

        Certificado certificado = obterOuCriar(inscricao);

        var dados = new CertificadoPdfRenderer.DadosCertificado(
                sanitizarParaPdf(inscricao.getAluno().getNome()),
                inscricao.getAluno().getRgm() != null ? inscricao.getAluno().getRgm() : "-",
                sanitizarParaPdf(evento.getTitulo()),
                CertificadoPdfRenderer.formatarData(evento.getDataHoraInicio().toLocalDate()),
                cargaHoraria(evento),
                certificado.getCodigo(),
                urlValidacaoLegivel(baseUrl));

        byte[] qrCode = qrCodeService.gerarPng(baseUrl + "/validar/" + certificado.getCodigo());
        return pdfRenderer.renderizar(dados, qrCode);
    }

    /**
     * Reaproveita o certificado ja emitido (o aluno pode baixar quantas
     * vezes quiser, sempre com o MESMO codigo - senao um codigo impresso
     * antes deixaria de validar). Gera o codigo aqui tambem pra linhas
     * antigas, emitidas antes de o codigo existir.
     */
    private Certificado obterOuCriar(Inscricao inscricao) {
        Certificado certificado = certificadoRepository.findByInscricaoId(inscricao.getId())
                .orElseGet(() -> certificadoRepository.save(Certificado.builder()
                        .inscricao(inscricao)
                        .codigo(codigoGenerator.gerar())
                        .build()));

        if (certificado.getCodigo() == null || certificado.getCodigo().isBlank()) {
            certificado.setCodigo(codigoGenerator.gerar());
            certificado = certificadoRepository.save(certificado);
        }
        return certificado;
    }

    /** Pagina publica de validacao - ver CertificadoValidacaoResponse
     *  sobre por que o RGM nao entra na resposta. */
    public CertificadoValidacaoResponse validar(String codigo) {
        Certificado certificado = certificadoRepository.findByCodigo(normalizar(codigo))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Nenhum certificado encontrado para o codigo informado"));

        Evento evento = certificado.getInscricao().getEvento();
        return new CertificadoValidacaoResponse(
                certificado.getCodigo(),
                certificado.getInscricao().getAluno().getNome(),
                evento.getTitulo(),
                evento.getDataHoraInicio().toLocalDate(),
                cargaHoraria(evento),
                certificado.getEmitidoEm());
    }

    /**
     * Aceita o codigo digitado com variacoes triviais (minusculo, espacos
     * sobrando, sem os hifens) - quem digita esta lendo de um papel
     * impresso, nao vale reprovar por causa de formatacao.
     */
    private String normalizar(String codigo) {
        if (codigo == null) {
            return "";
        }
        String limpo = codigo.trim().toUpperCase().replace(" ", "");
        if (!limpo.contains("-") && limpo.length() == 13 && limpo.startsWith("WCT")) {
            limpo = limpo.substring(0, 3) + "-" + limpo.substring(3, 7) + "-" + limpo.substring(7);
        }
        return limpo;
    }

    /**
     * Carga horaria calculada da duracao do proprio evento, em vez de um
     * campo separado no cadastro: um campo manual poderia divergir do
     * horario real da palestra (ex.: evento cadastrado das 19h as 21h,
     * mas com "4h" digitado na mao) e o certificado sairia com uma
     * carga que o evento nao teve.
     */
    private String cargaHoraria(Evento evento) {
        Duration duracao = Duration.between(evento.getDataHoraInicio(), evento.getDataHoraFim());
        long horas = duracao.toHours();
        long minutos = duracao.toMinutesPart();

        // Abaixo de uma hora sai como "45min" em vez de "0h45" - so
        // acontece em evento curto (teste), mas "0h05" impresso num
        // certificado fica estranho.
        if (horas == 0) {
            return minutos + "min";
        }
        return minutos == 0 ? horas + "h" : String.format("%dh%02d", horas, minutos);
    }

    /** Versao curta da URL pro texto impresso ("wecti.com.br/validar"),
     *  sem o "https://" - o link completo ja esta dentro do QR code. */
    private String urlValidacaoLegivel(String baseUrl) {
        return baseUrl.replaceFirst("^https?://", "") + "/validar";
    }

    /**
     * As fontes padrao do PDFBox (Helvetica) so cobrem Latin-1/WinAnsi -
     * um caractere fora disso (emoji, por exemplo) em nome de aluno ou
     * titulo de evento faz o PDFBox lancar IllegalArgumentException e
     * derrubar a geracao do certificado. Troca por "?" em vez de crashar.
     */
    private static String sanitizarParaPdf(String texto) {
        if (texto == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            sb.append(c <= 0xFF ? c : '?');
        }
        return sb.toString();
    }
}

package com.wecti.api.exception;

import com.wecti.api.dto.ErroResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> handleNaoEncontrado(RecursoNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErroResponse.de(404, "recurso_nao_encontrado", ex.getMessage()));
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErroResponse> handleRegraNegocio(RegraNegocioException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(ErroResponse.de(422, "regra_de_negocio", ex.getMessage()));
    }

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<ErroResponse> handleConflito(ConflitoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErroResponse.de(409, "conflito", ex.getMessage()));
    }

    /** Conta bloqueada por falhas seguidas (LimiteTentativasPorConta).
     *  Retry-After diz ao cliente em quantos segundos tentar de novo. */
    @ExceptionHandler(MuitasTentativasException.class)
    public ResponseEntity<ErroResponse> handleMuitasTentativas(MuitasTentativasException ex) {
        return ResponseEntity.status(429)
                .header("Retry-After", String.valueOf(ex.getSegundosAteLiberar()))
                .body(ErroResponse.de(429, "muitas_tentativas", ex.getMessage()));
    }

    @ExceptionHandler({CredenciaisInvalidasException.class, BadCredentialsException.class})
    public ResponseEntity<ErroResponse> handleCredenciaisInvalidas(RuntimeException ex) {
        // Usa a mensagem da propria excecao (nao mais fixa em "Email ou
        // senha invalidos") - esse handler tambem cobre o fluxo de
        // "esqueci minha senha" (UsuarioService.redefinirSenha), onde nao
        // ha campo "senha" nenhum envolvido na verificacao de identidade;
        // as duas chamadas ja usam mensagens seguras (sem vazar qual
        // parte especifica nao bateu).
        String mensagem = ex.getMessage() != null ? ex.getMessage() : "Email ou senha invalidos";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErroResponse.de(401, "credenciais_invalidas", mensagem));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResponse> handleAcessoNegado(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErroResponse.de(403, "acesso_negado", "Perfil sem permissao para esta operacao"));
    }

    @ExceptionHandler(CampoInvalidoException.class)
    public ResponseEntity<ErroResponse> handleCampoInvalido(CampoInvalidoException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErroResponse.de(400, "campo_invalido", ex.getMessage(), ex.getCampo()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> handleValidacao(MethodArgumentNotValidException ex) {
        var erro = ex.getBindingResult().getFieldErrors().stream().findFirst();
        String campo = erro.map(e -> e.getField()).orElse(null);
        String mensagem = erro.map(e -> e.getDefaultMessage()).orElse("Dados invalidos");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErroResponse.de(400, "campo_invalido", mensagem, campo));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErroResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErroResponse.de(400, "campo_invalido", ex.getMessage()));
    }

    /**
     * Valor na URL que nao converte para o tipo esperado - tipicamente um
     * UUID malformado em /eventos/{id}, /usuarios/{id}, etc.
     *
     * <p>Sem isto, cai no tratador generico e vira <b>500 com stack trace
     * no log</b>: um erro de quem chamou, registrado como falha do
     * servidor. Basta alguem digitar uma URL errada ou um link antigo
     * quebrar para poluir o log e assustar quem for investigar.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResponse> handleTipoInvalidoNaUrl(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErroResponse.de(400, "campo_invalido",
                        "Valor invalido para '" + ex.getName() + "' no endereco da requisicao", ex.getName()));
    }

    /**
     * Metodo HTTP que a rota nao aceita - por exemplo GET em
     * /usuarios/{id}, que so responde a PUT e DELETE.
     *
     * <p>Mesmo problema do handler acima: sem tratamento vira 500 com
     * stack trace, dizendo "o servidor quebrou" quando o certo e 405
     * ("esse endereco existe, mas nao com esse metodo").
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResponse> handleMetodoNaoSuportado(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErroResponse.de(405, "metodo_nao_permitido",
                        "O metodo " + ex.getMethod() + " nao e aceito neste endereco"));
    }

    /** JSON malformado, enum/UUID invalido no corpo da requisicao, etc. -
     *  sem isso, cai no tratamento padrao do Spring (fora do nosso
     *  formato de resposta), mas ainda assim como 400, nao como 500. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> handleCorpoInvalido(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErroResponse.de(400, "campo_invalido", "Corpo da requisicao invalido ou malformado"));
    }

    /**
     * URL que nao corresponde a nenhuma rota. Sem este tratamento, cai no
     * handler generico abaixo e vira 500 com stack trace no log - o que e
     * errado em dois sentidos: 500 diz "o servidor quebrou" quando na
     * verdade o endereco e que nao existe, e em producao qualquer robo
     * varrendo enderecos encheria o log de stack trace.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResponse> handleRotaInexistente(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErroResponse.de(404, "recurso_nao_encontrado", "Endereco nao encontrado"));
    }

    /**
     * Rede de seguranca final: qualquer excecao nao mapeada explicitamente
     * (bug, falha de I/O, etc.) vira um 500 controlado no nosso formato,
     * em vez de vazar a pagina de erro padrao do Spring (ou pior, um
     * stack trace) para o cliente. O detalhe do erro vai só pro log.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> handleGenerico(Exception ex) {
        log.error("Erro interno nao tratado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErroResponse.de(500, "erro_interno", "Ocorreu um erro interno. Tente novamente mais tarde."));
    }
}

package com.portscape.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.portscape.baseline.BaselineNotAllowedException;
import com.portscape.scan.exception.InvalidTargetException;
import com.portscape.scan.exception.ScanException;
import com.portscape.scan.exception.ScanQueueFullException;

/**
 * Converte as excecoes de scan em respostas RFC 7807, com o {@code code} da excecao
 * numa propriedade propria: o cliente distingue causas sem interpretar mensagens.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidTargetException.class)
    public ProblemDetail handleInvalidTarget(InvalidTargetException e) {
        return problem(HttpStatus.BAD_REQUEST, "Target invalido", e);
    }

    /**
     * Rede de seguranca. Na pratica as falhas de scan ficam registadas no proprio job
     * (estado FAILED) e nao chegam aqui, porque o scan corre fora do pedido HTTP.
     */
    @ExceptionHandler(ScanException.class)
    public ProblemDetail handleScanFailure(ScanException e) {
        log.warn("Falha de scan a chegar ao handler HTTP [{}]", e.code(), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Falha no scan", e);
    }

    /**
     * 503 e nao 500: o pedido estava correto, so nao ha capacidade agora. O cliente
     * pode voltar a tentar sem mudar nada.
     */
    @ExceptionHandler(ScanQueueFullException.class)
    public ProblemDetail handleQueueFull(ScanQueueFullException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Fila de scans cheia", e);
    }

    @ExceptionHandler(BaselineNotAllowedException.class)
    public ProblemDetail handleBaselineNotAllowed(BaselineNotAllowedException e) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        detail.setTitle("Baseline invalido");
        detail.setProperty("code", "BASELINE_NOT_ALLOWED");
        return detail;
    }

    private static ProblemDetail problem(HttpStatus status, String title, ScanException e) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        detail.setTitle(title);
        detail.setProperty("code", e.code());
        return detail;
    }
}

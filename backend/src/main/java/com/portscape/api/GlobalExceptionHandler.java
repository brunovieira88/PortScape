package com.portscape.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.portscape.scan.exception.InvalidTargetException;
import com.portscape.scan.exception.ScanException;

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

    private static ProblemDetail problem(HttpStatus status, String title, ScanException e) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        detail.setTitle(title);
        detail.setProperty("code", e.code());
        return detail;
    }
}

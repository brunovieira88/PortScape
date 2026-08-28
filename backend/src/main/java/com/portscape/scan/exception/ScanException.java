package com.portscape.scan.exception;

/**
 * Base de todas as falhas de scan. Cada subclasse expoe um {@link #code()} estavel
 * que vai para a resposta HTTP, para o cliente poder distinguir causas sem
 * fazer parsing de mensagens.
 */
public abstract class ScanException extends RuntimeException {

    protected ScanException(String message) {
        super(message);
    }

    protected ScanException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract String code();
}

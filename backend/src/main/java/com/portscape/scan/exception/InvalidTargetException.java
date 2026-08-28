package com.portscape.scan.exception;

/** Target rejeitado: formato invalido ou fora das redes privadas permitidas. */
public class InvalidTargetException extends ScanException {

    public InvalidTargetException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "INVALID_TARGET";
    }
}

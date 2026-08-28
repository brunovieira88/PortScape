package com.portscape.baseline;

/** O scan indicado nao pode servir de baseline. Vira HTTP 400. */
public class BaselineNotAllowedException extends RuntimeException {

    public BaselineNotAllowedException(String message) {
        super(message);
    }
}

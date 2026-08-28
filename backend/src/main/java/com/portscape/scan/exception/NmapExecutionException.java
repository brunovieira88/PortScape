package com.portscape.scan.exception;

/** O processo do nmap terminou em erro, foi interrompido ou excedeu o timeout. */
public class NmapExecutionException extends ScanException {

    public NmapExecutionException(String message) {
        super(message);
    }

    public NmapExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String code() {
        return "NMAP_EXECUTION_FAILED";
    }
}

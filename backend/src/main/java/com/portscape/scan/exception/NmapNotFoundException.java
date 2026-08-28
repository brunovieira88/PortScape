package com.portscape.scan.exception;

/** O binario do nmap nao existe ou nao e executavel no caminho configurado. */
public class NmapNotFoundException extends ScanException {

    public NmapNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String code() {
        return "NMAP_NOT_FOUND";
    }
}

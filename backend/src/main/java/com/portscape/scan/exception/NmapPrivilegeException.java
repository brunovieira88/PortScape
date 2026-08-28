package com.portscape.scan.exception;

/**
 * O nmap recusou o scan por falta de privilegios (-sS e -O precisam de root).
 * A mensagem inclui as instrucoes de setup para o utilizador nao ter de adivinhar.
 */
public class NmapPrivilegeException extends ScanException {

    public NmapPrivilegeException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "NMAP_PRIVILEGE";
    }
}

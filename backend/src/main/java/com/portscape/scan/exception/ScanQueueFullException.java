package com.portscape.scan.exception;

/**
 * A fila de scans esta cheia e o pedido nao foi aceite.
 *
 * <p>Os scans correm um de cada vez (ver {@code AsyncConfig}) e a fila tem tamanho
 * fixo. Recusar de forma explicita e melhor do que aceitar um scan que ninguem vai
 * correr: o cliente sabe que pode tentar outra vez daqui a pouco.
 */
public class ScanQueueFullException extends ScanException {

    public ScanQueueFullException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "SCAN_QUEUE_FULL";
    }
}

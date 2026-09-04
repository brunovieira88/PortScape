package com.portscape.scan.exception;

/**
 * Pediu-se para cancelar um scan que ja tinha terminado.
 *
 * <p>Nao e um erro do pedido -- o id existe e o pedido esta bem formado -- e o que
 * esta errado e o estado do recurso. Dai o 409 no {@code GlobalExceptionHandler}: um
 * cliente que sonde de 1500 em 1500 ms pode sempre carregar em cancelar no mesmo
 * instante em que o scan acaba, e isso tem de se distinguir de um pedido invalido.
 */
public class ScanNotCancellableException extends ScanException {

    public ScanNotCancellableException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "SCAN_NOT_CANCELLABLE";
    }
}

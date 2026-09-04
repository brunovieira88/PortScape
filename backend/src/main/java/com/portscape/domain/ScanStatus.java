package com.portscape.domain;

public enum ScanStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    /** Parado a pedido do utilizador. Nao e uma falha, e nao guarda resultados. */
    CANCELLED
}

package com.portscape.baseline;

/**
 * Como um host se compara com o baseline.
 *
 * <p>{@code UNKNOWN} nao e "sem mudancas": e "nao ha com que comparar" (primeiro scan
 * de uma rede). Confundir os dois faria uma rede nunca antes vista parecer estavel.
 */
public enum HostChange {
    NEW,
    CHANGED,
    UNCHANGED,
    UNKNOWN
}

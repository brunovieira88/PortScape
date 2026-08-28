package com.portscape.risk.nvd;

/**
 * O NVD nao respondeu como devia.
 *
 * <p>Nao herda de {@code ScanException}: uma falha aqui nunca deve reprovar um scan.
 * Serve so para o {@link CveLookupService} saber que o resultado ficou incompleto e
 * marcar {@code cveLookupDegraded}, para o cliente nao confundir "sem CVEs" com
 * "nao foi possivel verificar".
 */
public class NvdUnavailableException extends RuntimeException {

    public NvdUnavailableException(Throwable cause) {
        super("Nao foi possivel consultar o NVD: " + cause, cause);
    }
}

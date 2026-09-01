package com.portscape.baseline;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.portscape.domain.Host;

/**
 * Comparacao entre um scan e o seu baseline.
 *
 * @param baselineScanId  o scan usado como referencia, ou null se nao houver
 * @param changeByIp      estado de cada host do scan atual, indexado pelo IP que ele
 *                        tem <i>neste</i> scan. O emparelhamento com o baseline e que
 *                        e feito por {@link Host#identity()}: um dispositivo que mudou
 *                        de IP continua a ser o mesmo e nao aparece como novo
 * @param disappeared     hosts que existiam no baseline e ja nao respondem. Nao tem
 *                        edificio na cidade, mas numa auditoria interessam tanto como
 *                        os novos -- uma maquina que desapareceu tambem e uma mudanca
 */
public record ScanDiff(
        UUID baselineScanId,
        Map<String, HostChange> changeByIp,
        List<Host> disappeared
) {
    public ScanDiff {
        changeByIp = changeByIp == null ? Map.of() : Map.copyOf(changeByIp);
        disappeared = disappeared == null ? List.of() : List.copyOf(disappeared);
    }

    /** Sem baseline: nada e novo nem alterado, porque nao ha termo de comparacao. */
    public static ScanDiff none() {
        return new ScanDiff(null, Map.of(), List.of());
    }

    public HostChange changeFor(String ip) {
        return changeByIp.getOrDefault(ip, HostChange.UNKNOWN);
    }

    public boolean hasBaseline() {
        return baselineScanId != null;
    }
}

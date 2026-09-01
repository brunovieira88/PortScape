package com.portscape.baseline;

import java.util.List;
import java.util.UUID;

import com.portscape.domain.Host;

/**
 * O termo de comparacao de um scan: os dispositivos que se sabe pertencerem a esta rede.
 *
 * <p>Ja foi um unico scan -- o anterior -- e isso respondia mal a pergunta que uma
 * auditoria faz. Um dispositivo que se desligou ha tres scans deixava de aparecer como
 * offline, porque tambem ja nao estava no scan anterior; e um que vai e volta era
 * assinalado como <i>novo</i> de cada vez que reaparecia.
 *
 * <p>Agora e a uniao dos dispositivos vistos na janela do inventario, com o estado
 * <b>mais recente</b> de cada um. Quando o baseline e fixado a mao, e simplesmente o
 * scan fixado.
 *
 * @param scanId o scan mais recente que contribuiu para este inventario -- e o que a
 *               API expoe como {@code baselineScanId}, para se saber de onde veio
 * @param hosts  um por dispositivo, sem repetidos, na versao mais recente conhecida
 */
public record BaselineSnapshot(UUID scanId, List<Host> hosts) {

    public BaselineSnapshot {
        hosts = hosts == null ? List.of() : List.copyOf(hosts);
    }
}

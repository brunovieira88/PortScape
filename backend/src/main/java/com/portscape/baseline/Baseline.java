package com.portscape.baseline;

import java.time.Instant;
import java.util.UUID;

/** Um scan fixado como referencia para uma rede. */
public record Baseline(String target, UUID scanId, Instant pinnedAt) {
}

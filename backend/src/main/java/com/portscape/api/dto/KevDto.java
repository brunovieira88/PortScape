package com.portscape.api.dto;

import java.time.LocalDate;

import com.portscape.risk.kev.KevListing;

/**
 * A confirmacao da CISA de que esta falha foi observada a ser explorada.
 *
 * <p>A ausencia deste objeto num CVE nao e um atestado de seguranca: significa apenas
 * que nao consta do catalogo -- ver {@link com.portscape.risk.kev.KevCatalog}.
 */
public record KevDto(
        LocalDate dateAdded,
        boolean knownRansomwareUse,
        String vulnerabilityName,
        String requiredAction
) {
    public static KevDto from(KevListing listing) {
        return listing == null ? null : new KevDto(listing.dateAdded(),
                listing.knownRansomwareUse(), listing.vulnerabilityName(),
                listing.requiredAction());
    }
}

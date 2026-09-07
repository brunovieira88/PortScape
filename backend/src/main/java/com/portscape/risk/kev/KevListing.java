package com.portscape.risk.kev;

import java.time.LocalDate;

/**
 * A entrada de um CVE no catalogo da CISA -- ou seja, a confirmacao de que aquela
 * falha foi observada a ser explorada no mundo real.
 *
 * <p>Um CVE sem listagem nao e um CVE seguro: e um CVE sobre o qual a CISA nao se
 * pronunciou. A ausencia significa "nao consta", nunca "nao acontece".
 *
 * @param dateAdded          quando a CISA o acrescentou ao catalogo
 * @param knownRansomwareUse observado em campanhas de ransomware. E o unico campo do
 *                           feed que descreve <i>como</i> esta a ser usado, e o mais
 *                           persuasivo que a ferramenta consegue mostrar
 * @param vulnerabilityName  nome curto dado pela CISA, mais legivel que o ID
 * @param requiredAction     a remediacao que a CISA exige as agencias federais --
 *                           serve de conselho pronto para qualquer pessoa
 */
public record KevListing(
        LocalDate dateAdded,
        boolean knownRansomwareUse,
        String vulnerabilityName,
        String requiredAction
) {
}

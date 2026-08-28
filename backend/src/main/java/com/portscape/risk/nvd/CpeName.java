package com.portscape.risk.nvd;

import java.util.Optional;

/**
 * Converte o CPE 2.2 que o nmap emite ({@code cpe:/a:openbsd:openssh:9.6}) para a
 * forma 2.3 que a API do NVD aceita
 * ({@code cpe:2.3:a:openbsd:openssh:9.6:*:*:*:*:*:*:*}).
 */
public final class CpeName {

    private static final String PREFIX = "cpe:/";
    /** part, vendor, product, version, update, edition, language, sw_edition,
     *  target_sw, target_hw, other -- 11 campos depois de "cpe:2.3". */
    private static final int COMPONENTS = 11;

    private CpeName() {
    }

    /**
     * @return o CPE em forma 2.3, ou vazio se a entrada nao for utilizavel
     *
     * <p>Um CPE <b>sem versao</b> e recusado de proposito. {@code cpe:/a:busybox:busybox}
     * casaria com todos os CVEs alguma vez publicados para o BusyBox, e atribui-los a
     * este host seria inventar risco: sem versao nao ha forma de dizer que a maquina
     * esta vulneravel. Melhor nao pontuar do que pontuar mal.
     */
    public static Optional<String> toVersionedCpe23(String cpe22) {
        Optional<String[]> parts = components(cpe22);
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(build(parts.get()));
    }

    /**
     * Termos de pesquisa para o dicionario do NVD: <b>primeiro token</b> do produto
     * mais a versao.
     *
     * <p>O {@code keywordSearch} do NVD faz AND de todas as palavras, por isso quanto
     * mais especifico o termo, menos resultados. Na pratica
     * "dropbear_ssh_server 2017.75" devolve zero e "dropbear 2017.75" devolve o
     * produto certo -- o primeiro token e o unico pedaco do nome que o nmap e o NIST
     * costumam ter em comum.
     */
    public static Optional<String> toSearchTerms(String cpe22) {
        return components(cpe22).map(parts -> firstToken(parts[2]) + " " + parts[3]);
    }

    /** O produto tal como o nmap o nomeia, ex. {@code dropbear_ssh_server}. */
    public static Optional<String> productOf(String cpe22) {
        return components(cpe22).map(parts -> parts[2]);
    }

    public static Optional<String> versionOf(String cpe22) {
        return components(cpe22).map(parts -> parts[3]);
    }

    /** O pedaco do nome com maior hipotese de coincidir entre o nmap e o NIST. */
    public static String firstToken(String product) {
        int separator = product.indexOf('_');
        return separator < 0 ? product : product.substring(0, separator);
    }

    /** Os componentes de um CPE 2.2 utilizavel (part, vendor, product, version, ...). */
    private static Optional<String[]> components(String cpe22) {
        if (cpe22 == null || !cpe22.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String[] parts = cpe22.substring(PREFIX.length()).split(":", -1);
        if (parts.length < 4) {
            return Optional.empty();
        }
        String version = parts[3];
        if (parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()
                || version.isBlank() || "*".equals(version) || "-".equals(version)) {
            return Optional.empty();
        }
        return Optional.of(parts);
    }

    private static String build(String[] parts) {
        StringBuilder cpe23 = new StringBuilder("cpe:2.3");
        String[] fields = new String[COMPONENTS];
        java.util.Arrays.fill(fields, "*");
        for (int i = 0; i < Math.min(parts.length, COMPONENTS); i++) {
            fields[i] = parts[i].isBlank() ? "*" : parts[i];
        }
        for (String field : fields) {
            cpe23.append(':').append(field);
        }
        return cpe23.toString();
    }
}

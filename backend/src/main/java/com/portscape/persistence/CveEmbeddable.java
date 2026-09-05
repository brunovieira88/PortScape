package com.portscape.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.portscape.risk.kev.KevListing;
import com.portscape.risk.nvd.Cve;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Um CVE gravado contra a porta onde foi encontrado.
 *
 * <p>{@code @Embeddable} e nao entidade propria: um CVE nao tem vida fora da porta a
 * que esta anexado -- nao se consulta por si, nao se partilha entre portas, e
 * desaparece com ela. E a mesma escolha que o {@code port_cpe} ja faz.
 *
 * <p>Achatado em colunas em vez de JSON porque, ao contrario do
 * {@link CveLookupEntity}, isto nao e uma cache descartavel: e o que o NVD sabia no
 * dia do scan, e um scan antigo tem de continuar a mostrar isso.
 */
@Embeddable
public class CveEmbeddable {

    @Column(name = "cve_id", nullable = false, length = 32)
    private String cveId;

    /**
     * {@code NUMERIC(3,1)} e nao {@code double}: o CVSS tem uma casa decimal e um 8.1
     * gravado como binario flutuante volta como 8.100000000000001 no painel.
     */
    @Column(name = "cvss_score")
    private BigDecimal cvssScore;

    @Column(name = "severity", length = 16)
    private String severity;

    /** 256: um vector CVSS v4.0 do NVD tem 174 caracteres. Ver a V7. */
    @Column(name = "cvss_vector", length = 256)
    private String vector;

    @Column(name = "published_at")
    private Instant published;

    @Column(name = "description")
    private String description;

    /** Null aqui significa "nao consta do catalogo da CISA", nao "nao e explorado". */
    @Column(name = "kev_date_added")
    private LocalDate kevDateAdded;

    @Column(name = "kev_ransomware")
    private Boolean kevRansomware;

    @Column(name = "kev_name")
    private String kevName;

    @Column(name = "kev_action")
    private String kevAction;

    protected CveEmbeddable() {
        // exigido pelo JPA
    }

    public static CveEmbeddable from(Cve cve) {
        CveEmbeddable entity = new CveEmbeddable();
        entity.cveId = cve.id();
        entity.cvssScore = cve.cvssScore() == null ? null : BigDecimal.valueOf(cve.cvssScore());
        entity.severity = cve.severity();
        entity.vector = cve.vector();
        entity.published = cve.published();
        entity.description = cve.description();
        if (cve.kev() != null) {
            entity.kevDateAdded = cve.kev().dateAdded();
            entity.kevRansomware = cve.kev().knownRansomwareUse();
            entity.kevName = cve.kev().vulnerabilityName();
            entity.kevAction = cve.kev().requiredAction();
        }
        return entity;
    }

    /**
     * A listagem KEV so se reconstroi se alguma coisa dela foi gravada. Um scan feito
     * com o catalogo desligado nao pode voltar da base de dados a dizer que a falha
     * consta dele.
     */
    public Cve toDomain() {
        boolean listed = kevDateAdded != null || kevRansomware != null || kevName != null;
        KevListing kev = listed
                ? new KevListing(kevDateAdded, Boolean.TRUE.equals(kevRansomware), kevName, kevAction)
                : null;
        return new Cve(cveId,
                cvssScore == null ? null : cvssScore.doubleValue(),
                severity, vector, published, description, kev);
    }
}

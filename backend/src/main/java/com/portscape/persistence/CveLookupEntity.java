package com.portscape.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Resposta do NVD guardada para um CPE.
 *
 * <p>O payload fica em JSON em vez de tabelas normalizadas de CVE de proposito: isto
 * e uma cache, nao um modelo de dominio. Se o formato do NVD mudar, deita-se fora e
 * volta-se a consultar -- normalizar so acrescentaria migracoes a manter.
 */
@Entity
@Table(name = "cve_lookup")
public class CveLookupEntity {

    @Id
    private String cpe;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected CveLookupEntity() {
        // exigido pelo JPA
    }

    public CveLookupEntity(String cpe, String payload, Instant fetchedAt) {
        this.cpe = cpe;
        this.payload = payload;
        this.fetchedAt = fetchedAt;
    }

    public String getCpe() {
        return cpe;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}

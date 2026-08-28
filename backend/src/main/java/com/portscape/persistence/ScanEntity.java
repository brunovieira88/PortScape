package com.portscape.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.portscape.domain.ScanStatus;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Espelho persistente de um {@link com.portscape.scan.ScanJob}.
 *
 * <p>Separada do dominio de proposito: {@code ScanJob}, {@code Host} e {@code Port}
 * sao records imutaveis sem qualquer anotacao JPA, e o mapeamento entre os dois
 * mundos vive no {@link com.portscape.scan.ScanJobStore}. Assim o pipeline de scan
 * nunca lida com entidades geridas nem com sessoes.
 *
 * <p>O id vem de fora (gerado no servico) em vez de ser gerado pela BD: o POST tem de
 * devolver o id no {@code Location} antes de o scan sequer arrancar.
 */
@Entity
@Table(name = "scan")
public class ScanEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "cve_lookup_degraded", nullable = false)
    private boolean cveLookupDegraded;

    /**
     * {@code orphanRemoval} e essencial: cada transicao do job regrava a lista de
     * hosts, e sem isto os hosts do estado anterior ficavam orfaos na tabela.
     *
     * <p>LAZY com {@code @BatchSize}: duas colecoes EAGER encadeadas (scan -> hosts
     * -> ports) fariam o Hibernate tentar juntar dois "bags" na mesma query e
     * rebentar com {@code MultipleBagFetchException}. Assim as portas de varios
     * hosts vem num punhado de queries em vez de uma por host.
     */
    @OneToMany(mappedBy = "scan", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @BatchSize(size = 64)
    @OrderBy("id ASC")
    private List<HostEntity> hosts = new ArrayList<>();

    protected ScanEntity() {
        // exigido pelo JPA
    }

    public ScanEntity(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public ScanStatus getStatus() {
        return status;
    }

    public void setStatus(ScanStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean isCveLookupDegraded() {
        return cveLookupDegraded;
    }

    public void setCveLookupDegraded(boolean cveLookupDegraded) {
        this.cveLookupDegraded = cveLookupDegraded;
    }

    public List<HostEntity> getHosts() {
        return hosts;
    }

    /** Substitui a lista mantendo a colecao gerida pelo Hibernate. */
    public void replaceHosts(List<HostEntity> replacements) {
        hosts.clear();
        for (HostEntity host : replacements) {
            host.setScan(this);
            hosts.add(host);
        }
    }
}

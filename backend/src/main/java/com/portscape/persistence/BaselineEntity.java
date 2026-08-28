package com.portscape.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Scan fixado como referencia para uma rede.
 *
 * <p>O {@code ON DELETE CASCADE} e importante: apagar um scan que estava fixado
 * remove tambem a fixacao, e a rede volta ao baseline implicito em vez de ficar a
 * apontar para nada.
 */
@Entity
@Table(name = "baseline")
public class BaselineEntity {

    @Id
    private String target;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_id", nullable = false)
    private ScanEntity scan;

    @Column(name = "pinned_at", nullable = false)
    private Instant pinnedAt;

    protected BaselineEntity() {
        // exigido pelo JPA
    }

    public BaselineEntity(String target, ScanEntity scan, Instant pinnedAt) {
        this.target = target;
        this.scan = scan;
        this.pinnedAt = pinnedAt;
    }

    public String getTarget() {
        return target;
    }

    public ScanEntity getScan() {
        return scan;
    }

    public void setScan(ScanEntity scan) {
        this.scan = scan;
    }

    public UUID getScanId() {
        return scan.getId();
    }

    public Instant getPinnedAt() {
        return pinnedAt;
    }

    public void setPinnedAt(Instant pinnedAt) {
        this.pinnedAt = pinnedAt;
    }
}

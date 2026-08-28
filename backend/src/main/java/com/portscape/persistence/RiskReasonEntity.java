package com.portscape.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "risk_reason")
public class RiskReasonEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private HostEntity host;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private int points;

    protected RiskReasonEntity() {
        // exigido pelo JPA
    }

    public RiskReasonEntity(String code, String description, int points) {
        this.code = code;
        this.description = description;
        this.points = points;
    }

    public Long getId() {
        return id;
    }

    void setHost(HostEntity host) {
        this.host = host;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getPoints() {
        return points;
    }
}

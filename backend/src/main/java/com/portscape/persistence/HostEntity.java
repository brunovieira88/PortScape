package com.portscape.persistence;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "host")
public class HostEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_id", nullable = false)
    private ScanEntity scan;

    @Column(nullable = false)
    private String ip;

    private String hostname;

    @Column(name = "os_guess")
    private String osGuess;

    @Column(name = "os_accuracy")
    private Integer osAccuracy;

    /** Null para scans da fase 1, anteriores ao scoring -- e para scans que falharam. */
    @Column(name = "risk_score")
    private Integer riskScore;

    @OneToMany(mappedBy = "host", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @BatchSize(size = 64)
    @OrderBy("number ASC")
    private List<PortEntity> ports = new ArrayList<>();

    protected HostEntity() {
        // exigido pelo JPA
    }

    @OneToMany(mappedBy = "host", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @BatchSize(size = 64)
    @OrderBy("id ASC")
    private List<RiskReasonEntity> riskReasons = new ArrayList<>();

    public HostEntity(String ip, String hostname, String osGuess, Integer osAccuracy) {
        this.ip = ip;
        this.hostname = hostname;
        this.osGuess = osGuess;
        this.osAccuracy = osAccuracy;
    }

    public Long getId() {
        return id;
    }

    public ScanEntity getScan() {
        return scan;
    }

    void setScan(ScanEntity scan) {
        this.scan = scan;
    }

    public String getIp() {
        return ip;
    }

    public String getHostname() {
        return hostname;
    }

    public String getOsGuess() {
        return osGuess;
    }

    public Integer getOsAccuracy() {
        return osAccuracy;
    }

    public List<PortEntity> getPorts() {
        return ports;
    }

    public void addPort(PortEntity port) {
        port.setHost(this);
        ports.add(port);
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }

    public List<RiskReasonEntity> getRiskReasons() {
        return riskReasons;
    }

    public void addRiskReason(RiskReasonEntity reason) {
        reason.setHost(this);
        riskReasons.add(reason);
    }
}

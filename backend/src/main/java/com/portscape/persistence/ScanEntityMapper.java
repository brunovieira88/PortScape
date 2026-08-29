package com.portscape.persistence;

import java.util.List;

import com.portscape.domain.Host;
import com.portscape.domain.Port;
import com.portscape.risk.RiskReason;
import com.portscape.risk.RiskScore;
import com.portscape.scan.ScanJob;

/**
 * Fronteira entre o dominio (records imutaveis) e o JPA (entidades mutaveis).
 *
 * <p>Existir isto isolado e o que permite ao resto da aplicacao nunca ver uma
 * entidade gerida: o {@link com.portscape.scan.ScanService} continua a trabalhar so
 * com {@code ScanJob} e {@code Host}, exatamente como na fase 1.
 */
public final class ScanEntityMapper {

    private ScanEntityMapper() {
    }

    /** Escreve o estado do job na entidade, substituindo os hosts que la estavam. */
    public static void apply(ScanJob job, ScanEntity entity) {
        entity.setTarget(job.target());
        entity.setStatus(job.status());
        entity.setCreatedAt(job.createdAt());
        entity.setStartedAt(job.startedAt());
        entity.setFinishedAt(job.finishedAt());
        entity.setErrorCode(job.errorCode());
        entity.setErrorMessage(job.errorMessage());
        entity.setCveLookupDegraded(job.cveLookupDegraded());
        entity.setProgress(job.progress());
        entity.replaceHosts(job.hosts().stream().map(ScanEntityMapper::toEntity).toList());
    }

    public static ScanJob toDomain(ScanEntity entity) {
        return new ScanJob(
                entity.getId(),
                entity.getTarget(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getHosts().stream().map(ScanEntityMapper::toDomain).toList(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.isCveLookupDegraded(),
                entity.getProgress());
    }

    private static HostEntity toEntity(Host host) {
        HostEntity entity = new HostEntity(
                host.ip(), host.hostname(), host.osGuess(), host.osAccuracy());
        for (Port port : host.ports()) {
            entity.addPort(new PortEntity(port.number(), port.protocol(), port.state(),
                    port.service(), port.product(), port.version(), port.cpes()));
        }
        if (host.risk() != null) {
            entity.setRiskScore(host.risk().score());
            for (RiskReason reason : host.risk().reasons()) {
                entity.addRiskReason(new RiskReasonEntity(
                        reason.code(), reason.description(), reason.points()));
            }
        }
        return entity;
    }

    private static Host toDomain(HostEntity entity) {
        List<Port> ports = entity.getPorts().stream()
                .map(port -> new Port(port.getNumber(), port.getProtocol(), port.getState(),
                        port.getService(), port.getProduct(), port.getVersion(), port.getCpes()))
                .toList();
        RiskScore risk = entity.getRiskScore() == null ? null : new RiskScore(
                entity.getRiskScore(),
                entity.getRiskReasons().stream()
                        .map(reason -> new RiskReason(
                                reason.getCode(), reason.getDescription(), reason.getPoints()))
                        .toList());
        return new Host(entity.getIp(), entity.getHostname(),
                entity.getOsGuess(), entity.getOsAccuracy(), ports, risk);
    }
}

package com.portscape.persistence;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "port")
public class PortEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private HostEntity host;

    /** "number" e reservada em SQL padrao -- as aspas evitam surpresas no dialeto. */
    @Column(name = "\"number\"", nullable = false)
    private int number;

    private String protocol;

    private String state;

    private String service;

    private String product;

    private String version;

    /**
     * Guardados para um scan relido da BD ficar igual ao que esteve em memoria --
     * sem isto, os CPEs desapareciam ao reiniciar e o painel de detalhes mostrava
     * menos informacao para scans antigos do que para o que acabou de correr.
     */
    @ElementCollection
    @CollectionTable(name = "port_cpe", joinColumns = @JoinColumn(name = "port_id"))
    @Column(name = "cpe", nullable = false)
    @BatchSize(size = 128)
    private List<String> cpes = new ArrayList<>();

    protected PortEntity() {
        // exigido pelo JPA
    }

    public PortEntity(int number, String protocol, String state,
                      String service, String product, String version, List<String> cpes) {
        this.number = number;
        this.protocol = protocol;
        this.state = state;
        this.service = service;
        this.product = product;
        this.version = version;
        this.cpes = new ArrayList<>(cpes);
    }

    public Long getId() {
        return id;
    }

    void setHost(HostEntity host) {
        this.host = host;
    }

    public HostEntity getHost() {
        return host;
    }

    public int getNumber() {
        return number;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getState() {
        return state;
    }

    public String getService() {
        return service;
    }

    public String getProduct() {
        return product;
    }

    public String getVersion() {
        return version;
    }

    public List<String> getCpes() {
        return cpes;
    }
}

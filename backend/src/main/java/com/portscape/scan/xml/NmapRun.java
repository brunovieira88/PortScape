package com.portscape.scan.xml;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Raiz do XML do nmap.
 *
 * <p>Este pacote espelha o formato do nmap um-para-um e nao deve sair do parser:
 * o resto da aplicacao trabalha com {@code com.portscape.domain}. Manter a
 * separacao significa que uma mudanca no formato do nmap se resolve aqui.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "nmaprun")
public class NmapRun {

    @JacksonXmlProperty(isAttribute = true)
    public String scanner;

    @JacksonXmlProperty(isAttribute = true)
    public String version;

    @JacksonXmlProperty(isAttribute = true)
    public String args;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "host")
    public List<XmlHost> hosts;
}

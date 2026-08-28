package com.portscape.scan.xml;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class XmlService {

    @JacksonXmlProperty(isAttribute = true)
    public String name;

    @JacksonXmlProperty(isAttribute = true)
    public String product;

    @JacksonXmlProperty(isAttribute = true)
    public String version;

    @JacksonXmlProperty(isAttribute = true)
    public String extrainfo;

    /**
     * Identificadores CPE do servico, ex. {@code cpe:/a:openbsd:openssh:9.6}. Sao
     * eles -- e nao o texto de {@code product} -- que permitem perguntar ao NVD por
     * CVEs reais. Um servico pode ter mais do que um (aplicacao e sistema operativo).
     */
    @JacksonXmlProperty(localName = "cpe")
    @JacksonXmlElementWrapper(useWrapping = false)
    public List<String> cpe;
}

package com.portscape.scan.xml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class XmlOsMatch {

    @JacksonXmlProperty(isAttribute = true)
    public String name;

    /** Confianca do nmap no palpite, 0-100. */
    @JacksonXmlProperty(isAttribute = true)
    public Integer accuracy;
}

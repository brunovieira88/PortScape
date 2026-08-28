package com.portscape.scan.xml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class XmlStatus {

    @JacksonXmlProperty(isAttribute = true)
    public String state;

    @JacksonXmlProperty(isAttribute = true)
    public String reason;
}

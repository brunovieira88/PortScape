package com.portscape.scan.xml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class XmlPort {

    @JacksonXmlProperty(isAttribute = true)
    public String protocol;

    @JacksonXmlProperty(isAttribute = true)
    public Integer portid;

    @JacksonXmlProperty(localName = "state")
    public XmlPortState state;

    @JacksonXmlProperty(localName = "service")
    public XmlService service;
}

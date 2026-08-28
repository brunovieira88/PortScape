package com.portscape.scan.xml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
}

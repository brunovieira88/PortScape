package com.portscape.scan.xml;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class XmlPorts {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "port")
    public List<XmlPort> ports;
}

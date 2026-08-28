package com.portscape.scan.xml;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class XmlHostnames {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "hostname")
    public List<XmlHostname> hostnames;
}

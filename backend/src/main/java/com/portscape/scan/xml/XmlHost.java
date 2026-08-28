package com.portscape.scan.xml;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class XmlHost {

    @JacksonXmlProperty(localName = "status")
    public XmlStatus status;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "address")
    public List<XmlAddress> addresses;

    @JacksonXmlProperty(localName = "hostnames")
    public XmlHostnames hostnames;

    @JacksonXmlProperty(localName = "ports")
    public XmlPorts ports;

    @JacksonXmlProperty(localName = "os")
    public XmlOs os;
}

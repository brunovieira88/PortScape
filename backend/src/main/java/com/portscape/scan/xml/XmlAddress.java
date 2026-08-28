package com.portscape.scan.xml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class XmlAddress {

    @JacksonXmlProperty(isAttribute = true)
    public String addr;

    /** ipv4, ipv6 ou mac -- um host traz normalmente mais do que um. */
    @JacksonXmlProperty(isAttribute = true)
    public String addrtype;

    @JacksonXmlProperty(isAttribute = true)
    public String vendor;
}

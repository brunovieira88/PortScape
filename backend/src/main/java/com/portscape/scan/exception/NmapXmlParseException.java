package com.portscape.scan.exception;

/** O XML devolvido pelo nmap esta malformado ou nao tem a estrutura esperada. */
public class NmapXmlParseException extends ScanException {

    public NmapXmlParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public NmapXmlParseException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "NMAP_XML_PARSE_FAILED";
    }
}

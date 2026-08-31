package com.portscape.scan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import java.io.StringReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.dataformat.xml.XmlFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.portscape.domain.Host;
import com.portscape.domain.Port;
import com.portscape.scan.exception.NmapXmlParseException;
import com.portscape.scan.xml.NmapRun;
import com.portscape.scan.xml.XmlAddress;
import com.portscape.scan.xml.XmlHost;
import com.portscape.scan.xml.XmlOsMatch;
import com.portscape.scan.xml.XmlPort;

/**
 * Converte o XML do nmap no modelo de dominio.
 *
 * <p>O leitor de XML tem DTDs e entidades externas desligadas. O nmap emite sempre
 * um {@code <!DOCTYPE nmaprun ...>}, e sem isto qualquer XML que chegue aqui podia
 * puxar entidades externas (XXE) -- uma superficie que este parser nao precisa de ter.
 */
@Component
public class NmapXmlParser {

    private static final String ROOT_ELEMENT = "nmaprun";

    private final XMLInputFactory inputFactory = secureInputFactory();
    private final XmlMapper mapper;

    public NmapXmlParser() {
        this.mapper = XmlMapper.builder(XmlFactory.builder()
                        .xmlInputFactory(inputFactory)
                        .build())
                .build();
    }

    private static XMLInputFactory secureInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }

    /**
     * @return os hosts que responderam ao scan, so com as portas abertas
     * @throws NmapXmlParseException se o XML estiver malformado ou nao for um nmaprun
     */
    public List<Host> parse(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new NmapXmlParseException("O nmap nao devolveu XML.");
        }

        requireNmapRunRoot(xml);

        NmapRun run;
        try {
            run = mapper.readValue(xml, NmapRun.class);
        } catch (Exception e) {
            throw new NmapXmlParseException("Nao foi possivel interpretar o XML do nmap: " + e.getMessage(), e);
        }
        if (run == null) {
            throw new NmapXmlParseException("O XML do nmap esta vazio ou nao tem um elemento <nmaprun>.");
        }
        if (run.hosts == null) {
            // Scan valido que nao encontrou nada -- nao e erro.
            return List.of();
        }

        List<Host> hosts = new ArrayList<>();
        for (XmlHost xmlHost : run.hosts) {
            if (!isUp(xmlHost)) {
                continue;
            }
            String ip = ipOf(xmlHost);
            if (ip == null) {
                // Sem endereco nao ha nada de util a mostrar na cidade.
                continue;
            }
            hosts.add(new Host(ip, hostnameOf(xmlHost), osNameOf(xmlHost), osAccuracyOf(xmlHost), openPortsOf(xmlHost)));
        }
        return List.copyOf(hosts);
    }

    /**
     * Confirma que a raiz e mesmo um {@code <nmaprun>}.
     *
     * <p>O Jackson ignora o nome do elemento raiz ao desserializar: sem esta
     * verificacao, um XML valido mas de outra coisa qualquer -- uma pagina de erro de
     * um proxy, por exemplo -- desserializava para um {@code NmapRun} de campos todos
     * a null e saia daqui como "scan com sucesso, zero hosts". Uma rede a aparecer
     * vazia por engano e o pior resultado que este parser pode devolver.
     */
    private void requireNmapRunRoot(String xml) {
        XMLStreamReader reader = null;
        try {
            reader = inputFactory.createXMLStreamReader(new StringReader(xml));
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                if (!ROOT_ELEMENT.equals(reader.getLocalName())) {
                    throw new NmapXmlParseException(
                            "O XML nao e um resultado de nmap: elemento raiz <"
                                    + reader.getLocalName() + ">, esperado <" + ROOT_ELEMENT + ">.");
                }
                return;
            }
            throw new NmapXmlParseException("O XML do nmap nao tem elemento raiz.");
        } catch (XMLStreamException e) {
            throw new NmapXmlParseException("Nao foi possivel interpretar o XML do nmap: " + e.getMessage(), e);
        } finally {
            closeQuietly(reader);
        }
    }

    private static void closeQuietly(XMLStreamReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (XMLStreamException e) {
            // Fechar um leitor sobre uma String nao tem como falhar de forma util.
        }
    }

    private static boolean isUp(XmlHost host) {
        return host.status != null && "up".equalsIgnoreCase(host.status.state);
    }

    /** Prefere IPv4; cai para IPv6 se for o unico. Ignora o endereco MAC. */
    private static String ipOf(XmlHost host) {
        if (host.addresses == null) {
            return null;
        }
        String ipv6 = null;
        for (XmlAddress address : host.addresses) {
            if (address.addr == null) {
                continue;
            }
            if ("ipv4".equalsIgnoreCase(address.addrtype)) {
                return address.addr;
            }
            if ("ipv6".equalsIgnoreCase(address.addrtype) && ipv6 == null) {
                ipv6 = address.addr;
            }
        }
        return ipv6;
    }

    private static String hostnameOf(XmlHost host) {
        if (host.hostnames == null || host.hostnames.hostnames == null) {
            return null;
        }
        return host.hostnames.hostnames.stream()
                .map(name -> name.name)
                .filter(Objects::nonNull)
                .filter(name -> !name.isBlank())
                .findFirst()
                .orElse(null);
    }

    /** O nmap pode devolver varios palpites de OS; fica o de maior confianca. */
    private static XmlOsMatch bestOsMatch(XmlHost host) {
        if (host.os == null || host.os.osMatches == null) {
            return null;
        }
        return host.os.osMatches.stream()
                .filter(match -> match.name != null)
                .max(Comparator.comparingInt(match -> match.accuracy == null ? 0 : match.accuracy))
                .orElse(null);
    }

    private static String osNameOf(XmlHost host) {
        XmlOsMatch match = bestOsMatch(host);
        return match == null ? null : match.name;
    }

    private static Integer osAccuracyOf(XmlHost host) {
        XmlOsMatch match = bestOsMatch(host);
        return match == null ? null : match.accuracy;
    }

    /** Os CPEs so aparecem na fase de deteccao de versao; na descoberta a lista vem vazia. */
    private static List<String> cpesOf(XmlPort port) {
        if (port.service == null || port.service.cpe == null) {
            return List.of();
        }
        return port.service.cpe.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(cpe -> !cpe.isBlank())
                .distinct()
                .toList();
    }

    /**
     * O {@code --open} ja filtra do lado do nmap, mas o parser nao depende disso:
     * o XML pode vir de outra invocacao ou de um ficheiro guardado.
     */
    private static List<Port> openPortsOf(XmlHost host) {
        if (host.ports == null || host.ports.ports == null) {
            return List.of();
        }
        List<Port> ports = new ArrayList<>();
        for (XmlPort xmlPort : host.ports.ports) {
            if (xmlPort.portid == null || xmlPort.state == null || !"open".equalsIgnoreCase(xmlPort.state.state)) {
                continue;
            }
            ports.add(new Port(
                    xmlPort.portid,
                    xmlPort.protocol,
                    xmlPort.state.state,
                    xmlPort.service == null ? null : xmlPort.service.name,
                    xmlPort.service == null ? null : xmlPort.service.product,
                    xmlPort.service == null ? null : xmlPort.service.version,
                    cpesOf(xmlPort)));
        }
        ports.sort(Comparator.comparingInt(Port::number));
        return List.copyOf(ports);
    }
}

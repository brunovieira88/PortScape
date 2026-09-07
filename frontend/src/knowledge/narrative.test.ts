import { describe, expect, it } from 'vitest';
import type { Cve, Host, Port } from '../api/types';
import { attackPathFor } from './narrative';

/**
 * A narrativa não consulta nada e não gera texto: preenche moldes com o que já está no
 * JSON. O que estes testes protegem é sobretudo o que ela NÃO diz — não inventa um
 * caminho onde não há, não afirma o sistema operativo, e não descreve procedimento.
 */
const V31_RCE = 'CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H';
const V31_READ_ONLY = 'CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N';

function port(number: number, overrides: Partial<Port> = {}): Port {
  return { number, protocol: 'tcp', state: 'open', ...overrides };
}

function cve(id: string, overrides: Partial<Cve> = {}): Cve {
  return { id, cvssScore: 8.1, severity: 'HIGH', vector: V31_RCE, ...overrides };
}

function host(ports: Port[], overrides: Partial<Host> = {}): Host {
  return { ip: '192.168.1.10', portCount: ports.length, ports, ...overrides };
}

const tacticIds = (h: Host) => attackPathFor(h)?.tactics.map((t) => t.id);

describe('attackPathFor', () => {
  it('um host sem portas nao tem caminho -- inventar um destruia a credibilidade dos outros', () => {
    expect(attackPathFor(host([]))).toBeUndefined();
  });

  it('uma porta banal sem falhas nao gera narrativa', () => {
    // O 443 tem dossie mas nao tem attackTactics: nao e um problema por si.
    expect(attackPathFor(host([port(443, { service: 'https' })]))).toBeUndefined();
  });

  it('sem CVEs, a entrada e a porta que da acesso inicial', () => {
    // 21 (FTP) da credenciais e exfiltracao; 23 (Telnet) da acesso inicial. Entra-se
    // pelo Telnet, mesmo estando o FTP primeiro na lista.
    const path = attackPathFor(host([port(21, { service: 'ftp' }), port(23, { service: 'telnet' })]));

    expect(path?.entry).toContain('23/tcp (Telnet)');
    // A consequencia vem do dossie, que ja a diz melhor do que um molde diria.
    expect(path?.impact).toContain('reads the administrator credentials');
  });

  it('um CVE em exploracao activa ganha a um CVSS mais alto sem KEV', () => {
    // Gravidade teorica contra realidade observada: sao eixos diferentes.
    const path = attackPathFor(host([
      port(80, { service: 'http', cves: [cve('CVE-2020-0001', { cvssScore: 9.8 })] }),
      port(445, { service: 'microsoft-ds', cves: [cve('CVE-2017-7494', {
        cvssScore: 8.1,
        kev: { dateAdded: '2023-03-30', knownRansomwareUse: true },
      })] }),
    ]));

    expect(path?.entry).toContain('445/tcp');
    expect(path?.impact).toContain('CVE-2017-7494');
    expect(path?.impact).toContain('exploited in the wild');
    expect(path?.impact).toContain('ransomware');
  });

  it('o vector decide se ha execucao -- um CVE que so le nao permite correr nada', () => {
    const rce = host([port(445, { cves: [cve('CVE-1', { vector: V31_RCE })] })]);
    const readOnly = host([port(445, { cves: [cve('CVE-2', { vector: V31_READ_ONLY })] })]);

    expect(tacticIds(rce)).toContain('TA0002');
    expect(tacticIds(readOnly)).not.toContain('TA0002');
  });

  it('traduz o vector em consequencia, sem dizer como se faz', () => {
    const path = attackPathFor(host([port(445, { cves: [cve('CVE-2017-7494')] })]));

    expect(path?.impact).toContain('reachable from the network');
    expect(path?.impact).toContain('without an account');
    // A linha: mecanismo e consequencia, nunca procedimento.
    expect(path?.impact).not.toMatch(/exploit|payload|run |execute the/i);
  });

  it('o pivot so aparece quando ha por onde seguir', () => {
    const alone = attackPathFor(host([port(23, { service: 'telnet' })]));
    const withPivot = attackPathFor(host([
      port(23, { service: 'telnet' }), port(445, { service: 'microsoft-ds' })]));

    expect(alone?.pivot).toBeUndefined();
    expect(alone?.tactics.map((t) => t.id)).not.toContain('TA0008');
    expect(withPivot?.pivot).toContain('445/tcp');
    expect(withPivot?.tactics.map((t) => t.id)).toContain('TA0008');
  });

  it('as tacticas saem sem repetidos e pela ordem em que a cadeia se le', () => {
    const path = attackPathFor(host([
      port(23, { service: 'telnet' }), port(3389, { service: 'ms-wbt-server' })]));

    // Entra-se, colhem-se credenciais, anda-se de lado -- e nao por ordem numerica.
    expect(path?.tactics.map((t) => t.id)).toEqual(['TA0001', 'TA0006', 'TA0008']);
    expect(path?.tactics[0].url).toBe('https://attack.mitre.org/tactics/TA0001/');
  });

  it('nao afirma o sistema operativo -- o fingerprint do nmap e um palpite', () => {
    const path = attackPathFor(host([port(23, { service: 'telnet' })],
      { osGuess: 'Windows 7' }));

    expect(path?.entry).toContain('reported as Windows 7');
    expect(path?.entry).not.toContain('is Windows 7');
  });

  it('sem fingerprint nao inventa um', () => {
    const path = attackPathFor(host([port(23, { service: 'telnet' })]));

    expect(path?.entry).not.toContain('reported as');
  });
});

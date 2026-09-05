/**
 * O que cada porta é, porque é que um atacante se importa, e como se fecha.
 *
 * Nenhuma API responde a isto. O NVD não tem uma entrada a explicar o que é o Telnet,
 * porque isso é conhecimento e não dados — e é a lacuna que resta depois de os CVEs
 * chegarem ao painel. Uma porta pode não ter CVE nenhum e continuar a ser o pior
 * problema da rede: o Telnet não tem falha, o Telnet *é* a falha.
 *
 * Vive no frontend, ao lado do `cvss.ts`, e não no backend, por duas razões:
 *
 *   - o modo demo do GitHub Pages não tem backend, e é lá que está a maior parte de
 *     quem vê este projeto;
 *   - isto é apresentação. O `RiskScorer` não lê este ficheiro e não deve ler — o
 *     score sai dos pesos e dos CVEs, e o dossiê explica o que o score já decidiu.
 *
 * O que amarra as duas metades é o `ports.test.ts`: lê os `port-weights` do
 * `application.yml` e falha se uma porta com peso próprio ficar sem explicação.
 *
 * Texto em inglês, como o resto da UI.
 */

import type { MitreTacticId } from './mitre';

export interface PortDossier {
  /** O nome do protocolo, não do serviço que o nmap reportou. */
  name: string;
  /** O que é, numa frase. */
  summary: string;
  /** Porque é que ainda aparece numa rede real — sem isto o conselho soa a repreensão. */
  whyItExists: string;
  /**
   * O que o adversário ganha. Mecanismo e consequência, nunca procedimento: descreve-se
   * o que a exposição permite, não como se faz. É a linha do projeto e não se passa.
   */
  attackerValue: string;
  /** Acções concretas, a mais eficaz primeiro. */
  hardening: string[];
  /** O que usar em vez disto, quando existe substituto directo. */
  safeAlternative?: string;
  /**
   * As tácticas do ATT&CK que esta exposição serve, para a narrativa citar.
   *
   * Ausente nas portas que não são um problema por si — e essa ausência é o que impede
   * a narrativa de inventar um caminho de ataque a partir de um HTTPS bem configurado.
   */
  attackTactics?: MitreTacticId[];
}

const DOSSIERS: Record<number, PortDossier> = {
  21: {
    name: 'FTP',
    summary:
      'File transfer with no encryption. The login and the files themselves cross the '
      + 'network in the clear, on separate connections.',
    whyItExists:
      'Older than the web, and still the default upload path for NAS boxes, cameras, '
      + 'routers and anything that predates cloud storage.',
    attackerValue:
      'Credentials and file contents are readable by anyone positioned on the path. '
      + 'Many embedded devices also ship an anonymous account that was never turned off, '
      + 'so the contents may need no credential at all.',
    hardening: [
      'Replace with SFTP, which rides on SSH and encrypts everything.',
      'If the device only speaks FTP, put it behind a VLAN the internet cannot reach.',
      'Disable the anonymous account, and check what it could already read.',
    ],
    safeAlternative: 'SFTP over SSH (22)',
    attackTactics: ['TA0006', 'TA0010'],
  },

  23: {
    name: 'Telnet',
    summary:
      'Remote terminal sessions with no encryption at all. Everything — the username, '
      + 'the password, every command typed — travels the network in plain text.',
    whyItExists:
      'Predates SSH by about fifteen years. Survives in switches, printers, IPMI boards '
      + 'and industrial gear that was never updated and often cannot be.',
    attackerValue:
      'Anyone able to observe the traffic reads the administrator credentials without an '
      + 'exploit and without breaking anything. There is nothing to crack — the protocol '
      + 'hands them over by design.',
    hardening: [
      'Turn it off and use SSH instead.',
      "If the device cannot do SSH, restrict it to a management VLAN that isn't routed.",
      'Rotate every credential that has crossed this port — treat them as compromised.',
    ],
    safeAlternative: 'SSH (22)',
    attackTactics: ['TA0006', 'TA0001'],
  },

  445: {
    name: 'SMB',
    summary:
      "Windows file and printer sharing, and the transport for much of Windows' remote "
      + 'administration.',
    whyItExists:
      'Every Windows network uses it, and NAS devices expose it so Windows clients feel '
      + 'at home. Legitimate on a LAN; rarely legitimate anywhere else.',
    attackerValue:
      'Historically the most productive service on a Windows network: it has carried '
      + 'wormable remote-code-execution flaws, it leaks share and account names before '
      + 'authentication, and once a credential works here it usually works on every other '
      + 'machine that trusts the same domain.',
    hardening: [
      'Never expose it beyond the local network — no port forwarding, no VPN shortcut.',
      'Disable SMBv1 entirely; it has no safe configuration.',
      'Require SMB signing so sessions cannot be relayed to another host.',
    ],
    attackTactics: ['TA0001', 'TA0008'],
  },

  512: {
    name: 'rexec',
    summary:
      'Runs a command on a remote machine. Sends the username and password unencrypted '
      + 'to do it.',
    whyItExists:
      'A 1980s Unix remote-execution tool. If it is open today it is almost always '
      + 'because a default install left it on, not because anything uses it.',
    attackerValue:
      'Command execution with credentials that are readable in transit. It is the worst '
      + 'of Telnet and remote shell at once.',
    hardening: ['Disable it. There is no configuration that makes it safe.'],
    safeAlternative: 'SSH (22)',
    attackTactics: ['TA0001', 'TA0002'],
  },

  513: {
    name: 'rlogin',
    summary:
      'Remote login that can authenticate on the *word* of the client machine, through '
      + 'trust files like `.rhosts`, with no password at all.',
    whyItExists:
      'Same generation as rexec and rsh. Survives on legacy Unix images and appliances '
      + 'nobody has rebuilt.',
    attackerValue:
      'Trust is based on source address and claimed username — both of which an attacker '
      + 'on the network controls. A machine that trusts another gives access to whoever '
      + 'can convincingly claim to be it.',
    hardening: ['Disable it, and remove any `.rhosts` or `hosts.equiv` files it relied on.'],
    safeAlternative: 'SSH (22)',
    attackTactics: ['TA0001', 'TA0002'],
  },

  514: {
    name: 'rsh',
    summary: 'Remote shell over the same address-based trust as rlogin, with no encryption.',
    whyItExists:
      'The scripting companion to rlogin — old batch jobs and cluster tooling still '
      + 'reference it.',
    attackerValue:
      'Direct shell access on the same forgeable trust as rlogin, and everything the '
      + 'shell does is readable in transit.',
    hardening: ['Disable it and move any scripts that use it to SSH keys.'],
    safeAlternative: 'SSH (22)',
    attackTactics: ['TA0001', 'TA0002'],
  },

  3389: {
    name: 'RDP',
    summary: "Windows Remote Desktop — full graphical control of the machine.",
    whyItExists:
      'The standard way to administer a Windows box remotely, and often switched on for '
      + 'one support session and never switched off.',
    attackerValue:
      'A working credential here is the whole machine, with a desktop. It is also one of '
      + 'the most heavily scanned ports on the internet, both for credential reuse and for '
      + 'flaws that need no credential at all.',
    hardening: [
      'Do not expose it to the internet — reach it through a VPN instead.',
      'Require network-level authentication, so unauthenticated sessions never reach the desktop.',
      'Enable account lockout: this port is scanned continuously.',
    ],
    attackTactics: ['TA0001', 'TA0008'],
  },

  5900: {
    name: 'VNC',
    summary:
      'Remote screen sharing. The base protocol has no encryption, and its password '
      + 'scheme is capped at eight characters.',
    whyItExists:
      'The cross-platform way to see another machine’s screen. Common on Linux desktops, '
      + 'Raspberry Pis and media boxes.',
    attackerValue:
      'Full interactive control of the desktop. Many installations have no password at '
      + 'all, and where there is one the eight-character limit makes it weak by design.',
    hardening: [
      'Tunnel it through SSH rather than exposing the port.',
      'Set a password, and never leave the "view only, no auth" default.',
      'Bind it to localhost so only the SSH tunnel can reach it.',
    ],
    safeAlternative: 'VNC over an SSH tunnel',
    attackTactics: ['TA0001'],
  },

  6379: {
    name: 'Redis',
    summary:
      'An in-memory data store. For most of its life it shipped with no authentication, '
      + 'trusting the network to keep strangers out.',
    whyItExists:
      'Cache and queue for a huge number of applications. Usually meant to be reachable '
      + 'only by the app that owns it.',
    attackerValue:
      "Read and write access to everything the application keeps in memory — sessions, "
      + 'tokens, queued jobs. Its own config commands have historically allowed writing '
      + 'files outside the database as well.',
    hardening: [
      'Bind it to localhost, or to the private interface the app uses.',
      'Set `requirepass`, and turn on protected mode.',
      'Rename or disable the administrative commands the application does not need.',
    ],
    attackTactics: ['TA0006', 'TA0010'],
  },

  // As três seguintes existem para a ferramenta poder dizer "isto está bem". Sem elas
  // o painel é um alarme que toca sempre, e um alarme que toca sempre deixa de se ouvir.

  22: {
    name: 'SSH',
    summary:
      'Encrypted remote shell and file transfer. The modern replacement for Telnet, '
      + 'rlogin and rsh.',
    whyItExists: 'The standard way to administer anything that is not Windows.',
    attackerValue:
      'Little, by itself — this is a port that is *supposed* to be here. What matters is '
      + 'how it is configured: password logins invite guessing, and an outdated server '
      + 'may carry flaws of its own. Check the version, not the port.',
    hardening: [
      'Use keys and set `PasswordAuthentication no`.',
      'Disable direct root login.',
      'Keep the server current — the CVEs listed below apply to this exact version.',
    ],
  },

  80: {
    name: 'HTTP',
    summary: 'Unencrypted web traffic. Content and any credentials travel in the clear.',
    whyItExists:
      'Every device with a web interface starts here, and most redirect to HTTPS. On a '
      + 'local network it is unremarkable on its own.',
    attackerValue:
      'On its own, very little. It matters when the page behind it takes a login, because '
      + 'those credentials are then readable in transit — and because whatever software '
      + 'serves the page is itself worth checking.',
    hardening: [
      'Redirect to HTTPS and serve nothing sensitive over plain HTTP.',
      'Check what is actually running here — the risk is usually in the software, not the port.',
    ],
    safeAlternative: 'HTTPS (443)',
  },

  443: {
    name: 'HTTPS',
    summary: 'Encrypted web traffic. What a web service should be using.',
    whyItExists: 'The default for anything with a web interface, and correctly so.',
    attackerValue:
      'Nothing inherent to the port. The questions worth asking are about what is behind '
      + 'it: is the certificate valid, is the TLS version current, and does the software '
      + 'serving it have known flaws?',
    hardening: [
      'Keep the certificate valid and the TLS version current.',
      'Check the software version — the port being encrypted says nothing about what it serves.',
    ],
  },
};

/**
 * O dossiê de uma porta, se houver.
 *
 * A ausência é normal e não é erro: a maioria das portas que um scan encontra não tem
 * entrada, e o painel deve simplesmente não mostrar nada em vez de inventar.
 */
export function dossierFor(port: number): PortDossier | undefined {
  return DOSSIERS[port];
}

/** Todas as portas com dossiê. Existe para o teste de cobertura. */
export function documentedPorts(): number[] {
  return Object.keys(DOSSIERS).map(Number).sort((a, b) => a - b);
}

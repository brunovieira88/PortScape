import type { Cve, Host, Port } from '../api/types';
import { severityOf } from '../api/types';
import { type MitreTactic, type MitreTacticId, tacticsOf } from './mitre';
import { dossierFor } from './ports';

/**
 * O caminho provável, dito por extenso.
 *
 * O painel já tem todas as peças — a porta, o produto, a versão, o CVE, o KEV, o que o
 * protocolo é — e continua a pedir ao leitor que as junte de cabeça. Isto junta-as.
 *
 * **Composição por regras, não texto gerado.** Cada frase sai de um molde preenchido com
 * dados que já estão no JSON. Nada aqui inventa, e nada aqui consulta nada.
 *
 * **A linha.** Descreve-se o mecanismo e a consequência, nunca o procedimento: *"esta
 * falha é alcançável pela rede e não precisa de credenciais"* diz o que a exposição
 * significa; como chegar lá não está aqui e não vai estar.
 *
 * **A humildade do fingerprint.** O `osGuess` do nmap é o vizinho mais próximo na base
 * de dados dele, não uma leitura da máquina — o painel já o diz. A narrativa herda isso:
 * escreve *"reported as"*, nunca *"is"*.
 */
export interface AttackPath {
  /** Por onde se entra. */
  entry: string;
  /** O que isso dá a quem entra. */
  impact: string;
  /** O que se segue, quando há por onde seguir. */
  pivot?: string;
  tactics: MitreTactic[];
}

/** Portas por onde se anda de lado numa rede depois de se estar dentro de uma máquina. */
const LATERAL_PORTS: Record<number, string> = {
  445: 'SMB',
  3389: 'RDP',
  22: 'SSH',
};

interface Entry {
  port: Port;
  cve?: Cve;
  tactics: MitreTacticId[];
}

/**
 * @returns o caminho provável, ou `undefined` quando não há nada de sério a dizer.
 *
 * `undefined` é a resposta certa para a maioria dos hosts, e é deliberado: inventar um
 * caminho de ataque para um telemóvel com zero portas abertas destruiria a credibilidade
 * dos hosts onde isto importa.
 */
export function attackPathFor(host: Host): AttackPath | undefined {
  const entry = pickEntry(host.ports || []);
  if (!entry) {
    return undefined;
  }

  const tactics: MitreTacticId[] = [...entry.tactics];
  const pivot = pickPivot(host, entry.port);
  if (pivot) {
    tactics.push('TA0008');
  }

  return {
    entry: describeEntry(host, entry),
    impact: describeImpact(entry),
    pivot: pivot ? describePivot(pivot) : undefined,
    tactics: tacticsOf(tactics),
  };
}

/**
 * Por onde se entra, por esta ordem: um CVE confirmado em exploração activa, depois o
 * CVE de maior CVSS, depois a porta que o dossiê diz ser um problema por si.
 *
 * A ordem não é arbitrária. Um KEV ganha a um CVSS mais alto sem KEV porque mede outra
 * coisa: gravidade teórica contra realidade observada.
 */
function pickEntry(ports: Port[]): Entry | undefined {
  const withCves = ports.flatMap((port) =>
    (port.cves || []).map((cve) => ({ port, cve })));

  const exploited = withCves.filter(({ cve }) => cve.kev);
  const best = exploited.length > 0
    ? worstOf(exploited)
    : worstOf(withCves.filter(({ cve }) => cve.cvssScore != null));

  if (best) {
    // Um CVE que altera o alvo permite executar; um que só lê, não. Sai do vector, e
    // quando não há vector não se assume nada.
    const alters = /\/(VI|I):[HC]/.test(best.cve.vector || '');
    return {
      port: best.port,
      cve: best.cve,
      tactics: alters ? ['TA0001', 'TA0002'] : ['TA0001'],
    };
  }

  // Sem CVEs, a entrada é uma porta que o dossiê diz ser um problema por si.
  //
  // Ordena-se por "dá acesso inicial" (TA0001) e não pelo peso: os pesos vivem no
  // `application.yml` do backend, e duplicá-los aqui era pedir que divergissem. Uma
  // porta que dá entrada é, por definição, melhor candidata a entrada do que uma que só
  // deixa ler credenciais em trânsito. Empates desfeitos pelo número da porta, para a
  // narrativa não depender da ordem em que o nmap devolveu os serviços.
  const risky = ports
    .map((port) => ({ port, tactics: dossierFor(port.number)?.attackTactics }))
    .filter((candidate): candidate is { port: Port; tactics: MitreTacticId[] } =>
      candidate.tactics !== undefined && candidate.tactics.length > 0)
    .sort((a, b) => {
      const initial = Number(b.tactics.includes('TA0001')) - Number(a.tactics.includes('TA0001'));
      return initial !== 0 ? initial : a.port.number - b.port.number;
    });

  return risky.length > 0 ? { port: risky[0].port, tactics: risky[0].tactics } : undefined;
}

function worstOf(candidates: { port: Port; cve: Cve }[]): { port: Port; cve: Cve } | undefined {
  if (candidates.length === 0) return undefined;
  return [...candidates].sort((a, b) => (b.cve.cvssScore ?? 0) - (a.cve.cvssScore ?? 0))[0];
}

/** A porta por onde se anda de lado, se houver alguma além da de entrada. */
function pickPivot(host: Host, entry: Port): Port | undefined {
  return (host.ports || []).find(
    (port) => port.number !== entry.number && LATERAL_PORTS[port.number] !== undefined);
}

function describeEntry(host: Host, entry: Entry): string {
  const service = dossierFor(entry.port.number)?.name || entry.port.service || 'the service';
  const where = `${entry.port.number}/${entry.port.protocol} (${service})`;
  // "reported as" e nao "is": o fingerprint do nmap e o vizinho mais proximo na base de
  // dados dele, nao uma leitura da maquina.
  const os = host.osGuess ? `, on a host reported as ${host.osGuess}` : '';

  if (!entry.cve) {
    return `${where} is exposed${os}.`;
  }
  const running = [entry.port.product, entry.port.version].filter(Boolean).join(' ');
  return `${where}${running ? ` running ${running}` : ''} is exposed${os}.`;
}

function describeImpact(entry: Entry): string {
  if (!entry.cve) {
    // Sem CVE, quem descreve a consequencia e o dossie -- e ja o faz melhor.
    return dossierFor(entry.port.number)?.attackerValue
      || 'The exposure itself is what an attacker works with.';
  }

  const cve = entry.cve;
  const band = severityOf(cve);
  const score = cve.cvssScore != null ? ` (CVSS ${cve.cvssScore.toFixed(1)}, ${band})` : '';
  const reach = /\/AV:N/.test(cve.vector || '') ? 'reachable from the network' : null;
  const noAuth = /\/(PR|Au):N/.test(cve.vector || '') ? 'without an account' : null;
  const how = [reach, noAuth].filter(Boolean).join(', ');

  const exploited = cve.kev
    ? ` It is on CISA's list of vulnerabilities confirmed as exploited in the wild${
      cve.kev.knownRansomwareUse ? ', including in ransomware campaigns' : ''}.`
    : '';

  return `${cve.id}${score} affects it${how ? `, and is ${how}` : ''}.${exploited}`;
}

function describePivot(pivot: Port): string {
  const name = LATERAL_PORTS[pivot.number];
  return `From here, ${pivot.number}/${pivot.protocol} (${name}) is what the same network `
    + 'would be reached through next.';
}

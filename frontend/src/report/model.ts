import type { Cve, Host, Port, RiskBand, Scan } from '../api/types';
import { severityOf } from '../api/types';
import { type AttackPath, attackPathFor } from '../knowledge/narrative';
import { dossierFor } from '../knowledge/ports';

/**
 * O scan reduzido a documento, antes de haver sintaxe.
 *
 * Existe porque o relatório sai em duas formas — HTML para imprimir em PDF, e Markdown
 * para colar num ticket — e duas funções a decidir o mesmo conteúdo é como as coisas
 * divergem. Aqui decide-se **o que entra, por que ordem e com que palavras**; os
 * renderizadores só tratam de sintaxe.
 *
 * Nada aqui sabe o que é uma tabela de Markdown ou uma tag HTML.
 */

export interface ReportModel {
  target: string;
  /** Já formatado: `6 September 2026 at 14:34 UTC`. */
  when: string;
  deviceCount: number;
  /** Já formatado: `2m 12s`. Vazio quando o scan não tem duração. */
  duration: string;
  /** Presente só quando a consulta ao NVD ficou incompleta. */
  warning?: string;
  bands: BandCount[];
  inventory: InventoryRow[];
  /** Hosts que merecem secção própria, do mais grave para o menos. */
  hosts: HostReport[];
  /** Os que não merecem. Continuam no inventário. */
  quiet: string[];
}

export interface BandCount {
  label: string;
  count: number;
}

export interface InventoryRow {
  ip: string;
  name: string;
  score: number;
  portCount: number;
  worstFinding: string;
}

export interface HostReport {
  ip: string;
  name: string;
  /** `**Critical · 100 / 100** · QNAP · reported as Linux 3.2 - 4.9 · 5 open ports`. */
  identity: string[];
  attackPath?: AttackPath;
  remediation: RemediationLine[];
  ports: PortReport[];
}

export interface RemediationLine {
  action: string;
  /** `removes 69 points; score would be 73`, ou a versão para host saturado. */
  effect: string;
}

export interface PortReport {
  /** `445/tcp — microsoft-ds (Samba smbd 4.6.2)`. */
  title: string;
  dossier?: { name: string; body: string; hardening: string[] };
  cves: CveReport[];
  /** `Showing the 25 highest-scoring of 431 known CVEs.`, quando truncado. */
  truncated?: string;
}

export interface CveReport {
  id: string;
  url: string;
  /** `9.8 CRITICAL, actively exploited and used in ransomware campaigns`. */
  qualifier: string;
  description?: string;
}

const BAND_ORDER: RiskBand[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'];

const BAND_LABEL: Record<RiskBand, string> = {
  CRITICAL: 'Critical',
  HIGH: 'High',
  MEDIUM: 'Medium',
  LOW: 'Low',
  UNKNOWN: 'Unknown',
};

export function modelFor(scan: Scan): ReportModel {
  const hosts = [...(scan.hosts || [])].sort(byRiskThenAddress);
  const detailed = hosts.filter(worthDetailing);

  return {
    target: scan.target,
    when: formatMoment(scan.finishedAt || scan.createdAt),
    deviceCount: hosts.length,
    duration: formatDuration(scan.durationMs),
    // Se a consulta ao NVD ficou incompleta, o risco esta SUBESTIMADO. Num documento
    // lido sem a aplicacao ao lado, isso tem de estar no topo e nao numa nota.
    warning: scan.cveLookupDegraded
      ? 'The CVE lookup was incomplete for this scan. Some services could not be checked '
        + 'against the NVD, so the risk shown here is understated.'
      : undefined,
    bands: bandCounts(hosts),
    inventory: hosts.map(inventoryRow),
    hosts: detailed.map(hostReport),
    quiet: detailed.length > 0
      ? hosts.filter((host) => !worthDetailing(host)).map((host) => host.ip)
      : [],
  };
}

/** Nome de ficheiro estável e ordenável: `portscape-192-168-1-0-24-2026-09-06`. */
export function reportSlug(scan: Scan): string {
  const target = scan.target.replace(/[^a-zA-Z0-9]+/g, '-').replace(/^-|-$/g, '');
  const when = (scan.finishedAt || scan.createdAt || '').slice(0, 10) || 'undated';
  return `portscape-${target}-${when}`;
}

function bandCounts(hosts: Host[]): BandCount[] {
  const counts = new Map<RiskBand, number>();
  for (const host of hosts) {
    const band = host.riskBand || 'UNKNOWN';
    counts.set(band, (counts.get(band) || 0) + 1);
  }
  return BAND_ORDER
    .filter((band) => counts.has(band))
    .map((band) => ({ label: BAND_LABEL[band], count: counts.get(band)! }));
}

function inventoryRow(host: Host): InventoryRow {
  return {
    ip: host.ip,
    name: nameOf(host),
    score: host.riskScore ?? 0,
    portCount: host.portCount,
    worstFinding: worstFindingOf(host),
  };
}

function hostReport(host: Host): HostReport {
  return {
    ip: host.ip,
    name: nameOf(host),
    identity: [
      `${BAND_LABEL[host.riskBand || 'UNKNOWN']} · ${host.riskScore ?? 0} / 100`,
      host.vendor,
      // "reported as": o fingerprint do nmap e o vizinho mais proximo na base de dados
      // dele, nao uma leitura da maquina. O documento herda a humildade do painel.
      host.osGuess ? `reported as ${host.osGuess}` : null,
      `${host.portCount} open ${host.portCount === 1 ? 'port' : 'ports'}`,
    ].filter((part): part is string => Boolean(part)),
    attackPath: attackPathFor(host),
    remediation: (host.remediation || []).map((action) => ({
      action: action.action,
      effect: action.scoreAfter === host.riskScore
        ? `removes ${action.pointsRemoved} points; the score stays at ${action.scoreAfter}`
        : `removes ${action.pointsRemoved} points; score would be ${action.scoreAfter}`,
    })),
    ports: (host.ports || []).map(portReport),
  };
}

function portReport(port: Port): PortReport {
  const running = [port.product, port.version].filter(Boolean).join(' ');
  const dossier = dossierFor(port.number);
  const cves = port.cves || [];
  const total = port.cveTotal ?? cves.length;

  return {
    title: `${port.number}/${port.protocol} — ${port.service || 'unknown service'}`
      + (running ? ` (${running})` : ''),
    dossier: dossier
      ? {
        name: dossier.name,
        body: `${dossier.summary} ${dossier.attackerValue}`,
        hardening: dossier.hardening,
      }
      : undefined,
    cves: cves.map(cveReport),
    truncated: total > cves.length
      ? `Showing the ${cves.length} highest-scoring of ${total} known CVEs.`
      : undefined,
  };
}

function cveReport(cve: Cve): CveReport {
  const score = cve.cvssScore != null ? `${cve.cvssScore.toFixed(1)} ${severityOf(cve)}` : '';
  const exploited = cve.kev
    ? `actively exploited${cve.kev.knownRansomwareUse ? ' and used in ransomware campaigns' : ''}`
    : '';
  return {
    id: cve.id,
    url: cve.url || `https://nvd.nist.gov/vuln/detail/${cve.id}`,
    qualifier: [score, exploited].filter(Boolean).join(', '),
    description: cve.description || undefined,
  };
}

function worthDetailing(host: Host): boolean {
  return (host.riskReasons || []).length > 0
    || (host.ports || []).some((port) => (port.cves || []).length > 0);
}

/** O achado que resume o host numa linha de tabela. */
function worstFindingOf(host: Host): string {
  const cves = (host.ports || []).flatMap((port) => port.cves || []);
  const exploited = cves.find((cve) => cve.kev);
  if (exploited) {
    return `${exploited.id} (${exploited.cvssScore?.toFixed(1) ?? '—'}, actively exploited)`;
  }
  const worst = cves
    .filter((cve) => cve.cvssScore != null)
    .sort((a, b) => (b.cvssScore ?? 0) - (a.cvssScore ?? 0))[0];
  if (worst) {
    return `${worst.id} (${worst.cvssScore?.toFixed(1)})`;
  }
  const reasons = host.riskReasons || [];
  return reasons.length > 0
    ? [...reasons].sort((a, b) => b.points - a.points)[0].description
    : '—';
}

function nameOf(host: Host): string {
  // O sufixo da rede local so faz ruido numa lista onde todos o tem.
  return host.hostname?.replace(/\.(home|lan|local)$/i, '') || host.vendor || 'Unknown device';
}

function byRiskThenAddress(a: Host, b: Host): number {
  const score = (b.riskScore ?? 0) - (a.riskScore ?? 0);
  return score !== 0 ? score : addressKey(a.ip) - addressKey(b.ip);
}

/** `192.168.1.60` como número, para o `.60` não vir antes do `.9`. */
function addressKey(ip: string): number {
  const parts = ip.split('.').map(Number);
  return parts.length === 4 && parts.every((n) => Number.isInteger(n))
    ? parts.reduce((acc, n) => acc * 256 + n, 0)
    : Number.MAX_SAFE_INTEGER;
}

/**
 * `6 September 2026 at 14:32 UTC`.
 *
 * Locale e fuso fixos de propósito: o documento é para ser partilhado, e uma data que
 * muda consoante a máquina que a gerou não serve de registo.
 */
function formatMoment(iso: string | null | undefined): string {
  if (!iso) return '';
  const when = new Date(iso);
  if (Number.isNaN(when.getTime())) return '';
  const date = new Intl.DateTimeFormat('en-GB', {
    day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC',
  }).format(when);
  const time = new Intl.DateTimeFormat('en-GB', {
    hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'UTC',
  }).format(when);
  return `${date} at ${time} UTC`;
}

function formatDuration(ms: number | null | undefined): string {
  if (ms == null || ms <= 0) return '';
  const seconds = Math.round(ms / 1000);
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return rest === 0 ? `${minutes}m` : `${minutes}m ${rest}s`;
}

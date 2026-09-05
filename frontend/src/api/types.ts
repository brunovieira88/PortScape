/**
 * A forma do JSON que o backend serve, escrita uma vez.
 *
 * <p>E um espelho manual dos records em `backend/src/main/java/com/portscape/api/dto/`
 * -- nao ha geracao de codigo, e de proposito: um ficheiro que se le de uma vez custa
 * menos do que uma toolchain que so serve para isto. Em troca, quem mexer num DTO tem
 * de vir aqui. O `mock/demoScan.ts` existe para isso nao passar despercebido: a
 * fixture da demo e verificada contra estes tipos pelo `tsc`, portanto um campo que
 * mude de nome no backend rebenta o build assim que a fixture for actualizada.
 *
 * <p><b>Nulos.</b> O `ScanResponse` e o `HostDto` levam `@JsonInclude(NON_NULL)`, logo
 * os campos nulos nem chegam a aparecer no JSON; o `PortDto` nao leva, e os dele
 * chegam como `null` explicito. Os tipos aqui aceitam as duas formas (`?: T | null`)
 * porque as duas acontecem de facto -- e porque a fixture da demo, escrita a mao,
 * tambem usa `null` onde a API omitiria.
 */

/** Estado de um scan. Ver `domain/ScanStatus.java`. */
export type ScanStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED' | 'CANCELLED';

/** Faixa de risco, que e o que da a cor ao edificio. Ver `risk/RiskBand.java`. */
export type RiskBand = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'UNKNOWN';

/** Como o host se compara com o baseline. Ver `baseline/HostChange.java`. */
export type HostChange = 'NEW' | 'CHANGED' | 'UNCHANGED' | 'UNKNOWN' | 'DISAPPEARED';

/**
 * A confirmacao da CISA de que a falha foi observada a ser explorada. Ver `KevDto`.
 *
 * A ausencia deste objeto num CVE NAO e um atestado de seguranca: significa apenas que
 * nao consta do catalogo. O painel tem de dizer uma coisa e nao a outra.
 */
export interface Kev {
  dateAdded?: string | null;
  knownRansomwareUse: boolean;
  vulnerabilityName?: string | null;
  requiredAction?: string | null;
}

/**
 * Uma falha conhecida do servico de uma porta. Ver `CveDto`.
 *
 * O `vector` vem por traduzir do backend (`AV:N/AC:L/PR:N/...`): a traducao para
 * linguagem corrente e apresentacao, e faz-se em `knowledge/cvss.ts` -- que tem de
 * funcionar tambem no modo demo, onde nao ha backend nenhum.
 *
 * `severity` pode vir a null mesmo com `cvssScore` preenchido: ha CVEs no NVD sem
 * `baseSeverity` publicado. Nao inventar uma a partir do score no sitio errado -- usar
 * `severityOf()` deste ficheiro, para a regra ficar num sitio so.
 */
export interface Cve {
  id: string;
  cvssScore?: number | null;
  severity?: string | null;
  vector?: string | null;
  published?: string | null;
  description?: string | null;
  url?: string | null;
  kev?: Kev | null;
}

/** Uma porta aberta. Ver `PortDto`. */
export interface Port {
  number: number;
  protocol: string;
  state: string;
  service?: string | null;
  product?: string | null;
  version?: string | null;
  cpes?: string[] | null;
  /** Truncada em `portscape.nvd.max-cves-per-port`, do pior CVSS para o menos grave. */
  cves?: Cve[] | null;
  /**
   * Quantos existiam antes de truncar. Quando e maior que `cves.length`, o painel tem
   * de o dizer -- mostrar 5 sem dizer que eram 22 seria mentir por omissao.
   */
  cveTotal?: number;
}

/**
 * Codigos de razao de risco que o backend emite. Ver `risk/rules/*.java`.
 *
 * E uma uniao de literais e nao `string` de proposito: as fixtures da demo chegaram a
 * usar cinco codigos (`COMMON_PORT`, `HIGH_RISK_PORT`, `UNWEIGHTED_PORTS`,
 * `NOT_IN_BASELINE`, `VULNERABLE_SERVICE`) que o backend nunca emitiu, e o `tsc` nao deu
 * por nada porque o campo era `string`. Agora da.
 */
export const RISK_CODES = ['OPEN_PORT', 'NEW_PORT', 'UNKNOWN_HOST', 'KNOWN_CVE'] as const;
export type RiskCode = typeof RISK_CODES[number];

/** Uma parcela do score, com a explicacao que enche o painel. Ver `RiskReasonDto`. */
export interface RiskReason {
  code: RiskCode;
  description: string;
  points: number;
}

/**
 * A faixa que da a cor a um CVE, derivada do CVSS quando o NVD nao publica severidade.
 *
 * Os cortes sao os do proprio CVSS v3.1 (9.0 critico, 7.0 alto, 4.0 medio) e nao os do
 * `portscape.risk`: aqui esta a qualificar-se a falha, nao o host.
 */
export function severityOf(cve: Cve): RiskBand {
  const named = cve.severity?.toUpperCase();
  if (named === 'CRITICAL' || named === 'HIGH' || named === 'MEDIUM' || named === 'LOW') {
    return named;
  }
  const score = cve.cvssScore;
  if (score == null) return 'UNKNOWN';
  if (score >= 9) return 'CRITICAL';
  if (score >= 7) return 'HIGH';
  if (score >= 4) return 'MEDIUM';
  return 'LOW';
}

/** Coordenada no plano da cidade. O y e sempre 0 -- a altura vem do `portCount`. */
export interface Position {
  x: number;
  z: number;
}

/** A posicao de um host dentro do layout, com a faixa que lhe deu o bairro. */
export interface HostPosition extends Position {
  ip: string;
  band: RiskBand;
}

/** Um bairro: a fatia da cidade ocupada por uma faixa de risco. */
export interface District {
  band: RiskBand;
  x: number;
  width: number;
  depth: number;
  hostCount: number;
}

/**
 * A cidade calculada pelo backend (`layout/CityLayout.java`).
 *
 * <p>Nao vem na listagem de scans -- ver `ScanResponse.withoutHosts()` -- por isso e
 * opcional aqui, e o `buildCityGrid` tem de continuar a aguentar a sua ausencia.
 */
export interface CityLayout {
  positions?: Record<string, HostPosition>;
  districts?: District[];
  spacing: number;
  width: number;
  depth: number;
}

/** Um dispositivo na rede. Ver `HostDto`. */
export interface Host {
  ip: string;
  mac?: string | null;
  vendor?: string | null;
  hostname?: string | null;
  osGuess?: string | null;
  osAccuracy?: number | null;
  portCount: number;
  riskScore?: number | null;
  riskBand?: RiskBand | null;
  position?: Position | null;
  riskReasons?: RiskReason[];
  change?: HostChange;
  isNew?: boolean;
  isChanged?: boolean;
  ports?: Port[];
}

/** Porque falhou um scan. So vem preenchido quando o estado e FAILED. */
export interface ScanError {
  code: string;
  message: string;
}

/**
 * O envelope canonico de um scan (`ScanResponse`).
 *
 * <p>A listagem devolve isto sem `layout` e com `hosts`/`ruins` vazios: um historico
 * nao precisa da cidade toda. E a mesma forma, com menos preenchido.
 */
export interface Scan {
  id: string;
  target: string;
  status: ScanStatus;
  createdAt: string;
  startedAt?: string | null;
  finishedAt?: string | null;
  durationMs?: number | null;
  hostsUp: number;
  baselineScanId?: string | null;
  cveLookupDegraded?: boolean;
  progress?: number;
  layout?: CityLayout | null;
  hosts?: Host[];
  ruins?: Host[];
  error?: ScanError | null;
}

/** Baseline afixado para uma rede. Ver `BaselineDto`. */
export interface Baseline {
  target: string;
  scanId: string;
  pinnedAt: string;
}

/** Comparacao de um scan com o baseline da sua rede. Ver `ScanDiffResponse`. */
export interface ScanDiff {
  scanId: string;
  baselineScanId?: string | null;
  changeByIp: Record<string, HostChange>;
  disappeared?: Host[];
}

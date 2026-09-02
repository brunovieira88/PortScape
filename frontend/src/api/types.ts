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

/** Uma porta aberta. Ver `PortDto`. */
export interface Port {
  number: number;
  protocol: string;
  state: string;
  service?: string | null;
  product?: string | null;
  version?: string | null;
  cpes?: string[] | null;
}

/** Uma parcela do score, com a explicacao que enche o painel. Ver `RiskReasonDto`. */
export interface RiskReason {
  code: string;
  description: string;
  points: number;
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

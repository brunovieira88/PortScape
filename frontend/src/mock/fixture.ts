import type { CityLayout, District, Host, RiskReason, Scan } from '../api/types';
import { RISK_CODES } from '../api/types';

/**
 * Passar um ficheiro JSON pela forma da API, com o compilador a verificar.
 *
 * As fixtures daqui foram escritas à mão — o `demo-scan.json` é o que a demo estática
 * do GitHub Pages mostra a quem nunca vai correr o backend, o `sample-scan.json` é o
 * que os testes da cena usam. Até haver isto, nada garantia que continuassem a ter a
 * mesma forma que a API real serve: um campo que mudasse de nome no `HostDto` só dava
 * erro em runtime, e só na demo, que é precisamente onde ninguém está a olhar.
 *
 * O TypeScript alarga as strings de um módulo JSON para `string`, portanto um ficheiro
 * importado nunca encaixa directamente num tipo com uniões — `"DONE"` chega aqui como
 * `string` e não como `ScanStatus`. Em vez de deitar a verificação toda fora com um
 * `as Scan`, declara-se a mesma forma com as uniões alargadas: o parâmetro de `asScan`
 * é verificado a sério (um campo em falta ou com o tipo errado dá erro de compilação) e
 * só o estreitamento das uniões fica por afirmação.
 *
 * O que o compilador deixa passar por causa desse alargamento é verificado à mão em
 * `assertKnownRiskCodes`. Não é zelo a mais: as fixtures chegaram a usar cinco códigos
 * de risco que o backend nunca emitiu (`COMMON_PORT`, `HIGH_RISK_PORT`,
 * `UNWEIGHTED_PORTS`, `NOT_IN_BASELINE`, `VULNERABLE_SERVICE`) e ninguém deu por isso
 * durante meses, porque o campo era `string` dos dois lados.
 */
type WidenBands<T> = Omit<T, 'riskBand' | 'change' | 'band' | 'riskReasons'> & {
  riskBand?: string | null;
  change?: string;
  band?: string;
  riskReasons?: (Omit<RiskReason, 'code'> & { code: string })[];
};

export type JsonScan = Omit<Scan, 'status' | 'layout' | 'hosts' | 'ruins'> & {
  status: string;
  layout?: (Omit<CityLayout, 'districts'> & { districts?: WidenBands<District>[] }) | null;
  hosts?: WidenBands<Host>[];
  ruins?: WidenBands<Host>[];
};

export function asScan(json: JsonScan): Scan {
  assertKnownRiskCodes(json);
  return json as Scan;
}

/**
 * Rebenta o carregamento da fixture se ela usar um código que o backend não emite.
 *
 * Em runtime e não em tipos porque o alargamento do JSON não deixa alternativa — mas
 * as fixtures são importadas pelos testes, logo o `npm test` (e o CI, que o corre antes
 * do build) apanha isto antes de chegar a alguém.
 */
function assertKnownRiskCodes(json: JsonScan): void {
  const known = new Set<string>(RISK_CODES);
  for (const host of [...(json.hosts ?? []), ...(json.ruins ?? [])]) {
    for (const reason of host.riskReasons ?? []) {
      if (!known.has(reason.code)) {
        throw new Error(
          `Fixture com um codigo de risco que o backend nao emite: "${reason.code}" `
          + `(host ${host.ip}). Os validos sao ${RISK_CODES.join(', ')} `
          + `-- ver backend/src/main/java/com/portscape/risk/rules/.`);
      }
    }
  }
}

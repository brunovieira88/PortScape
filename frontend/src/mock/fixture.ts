import type { CityLayout, District, Host, Scan } from '../api/types';

/**
 * Passar um ficheiro JSON pela forma da API, com o compilador a verificar.
 *
 * <p>As fixtures daqui foram escritas a mao -- o `demo-scan.json` e o que a demo
 * estatica do GitHub Pages mostra a quem nunca vai correr o backend, o
 * `sample-scan.json` e o que os testes da cena usam. Ate aqui nada garantia que
 * continuassem a ter a mesma forma que a API real serve: um campo que mudasse de nome
 * no `HostDto` so dava erro em runtime, e so na demo, que e precisamente onde ninguem
 * esta a olhar.
 *
 * <p>O TypeScript alarga as strings de um modulo JSON para `string`, portanto um
 * ficheiro importado nunca encaixa directamente num tipo com unioes -- `"DONE"` chega
 * aqui como `string` e nao como `ScanStatus`. Em vez de deitar a verificacao toda fora
 * com um `as Scan`, declara-se a mesma forma com as unioes alargadas: o parametro de
 * {@link asScan} e verificado a serio (um campo em falta ou com o tipo errado da erro
 * de compilacao) e so o estreitamento das unioes fica por afirmacao.
 */
type WidenBands<T> = Omit<T, 'riskBand' | 'change' | 'band'> & {
  riskBand?: string | null;
  change?: string;
  band?: string;
};

export type JsonScan = Omit<Scan, 'status' | 'layout' | 'hosts' | 'ruins'> & {
  status: string;
  layout?: (Omit<CityLayout, 'districts'> & { districts?: WidenBands<District>[] }) | null;
  hosts?: WidenBands<Host>[];
  ruins?: WidenBands<Host>[];
};

export function asScan(json: JsonScan): Scan {
  return json as Scan;
}

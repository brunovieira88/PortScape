/**
 * Traducao do layout que o backend calcula para a grelha de quarteiroes da cena.
 *
 * <p>Vive fora do componente de proposito: e logica pura, e foi exatamente aqui que
 * um bug passou despercebido durante toda a fase 3 -- o frontend recompactava as
 * coordenadas e desfazia o trabalho do CityLayoutCalculator. Fora do React, testa-se.
 */

export interface CityGrid {
  spacing: number;
  hosts: PlacedHost[];
  ruins: PlacedHost[];
  districts: PlacedDistrict[];
  /** Dimensoes da cidade em quarteiroes. */
  layoutW: number;
  layoutD: number;
  /** Offset que poe o centro da cidade na origem, para a camara nascer no meio. */
  offsetX: number;
  offsetZ: number;
}

export interface PlacedHost {
  [key: string]: any;
  gridX: number;
  gridZ: number;
}

export interface PlacedDistrict {
  band: string;
  /** Coluna onde o bairro comeca, e o seu tamanho em quarteiroes. */
  startX: number;
  columns: number;
  rows: number;
}

/**
 * O backend ja entrega a cidade compactada: cada bairro e um bloco denso preenchido
 * por ordem de IP e nunca ha dois hosts na mesma celula. Aqui so se converte a
 * coordenada de mundo em indice de quarteirao.
 *
 * <p><b>Nao voltar a compactar aqui.</b> Arredondar para uma grelha mais apertada
 * colapsa varios hosts na mesma celula e obriga a desempatar por varrimento, o que faz
 * a posicao de um host depender de quais os outros hosts do scan e da ordem por que
 * foram processados -- e ai um edificio que se mexe deixa de querer dizer alguma coisa.
 * Se a cidade voltar a parecer vazia, o sitio de a apertar e o CityLayoutCalculator,
 * onde e determinista e tem testes.
 */
export function buildCityGrid(scanData: any): CityGrid {
  const spacing = scanData?.layout?.spacing || 1.0;
  const cell = (value: number) => Math.round(value / spacing);

  const place = (host: any): PlacedHost => ({
    ...host,
    gridX: cell(host?.position?.x ?? 0),
    gridZ: cell(host?.position?.z ?? 0),
  });

  // Um scan sem layout -- a listagem devolve os sumarios sem ele -- da uma cidade
  // vazia em vez de rebentar a cena.
  const layoutW = cell(scanData?.layout?.width ?? 0);
  const layoutD = cell(scanData?.layout?.depth ?? 0);

  return {
    spacing,
    hosts: (scanData?.hosts ?? []).map(place),
    ruins: (scanData?.ruins ?? []).map(place),
    districts: (scanData?.layout?.districts ?? []).map((district: any) => ({
      band: district.band,
      startX: cell(district.x),
      columns: Math.max(1, cell(district.width)),
      rows: Math.max(1, cell(district.depth)),
    })),
    layoutW,
    layoutD,
    offsetX: -layoutW / 2,
    offsetZ: -layoutD / 2,
  };
}

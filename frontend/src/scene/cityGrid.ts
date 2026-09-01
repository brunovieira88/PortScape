/**
 * Traducao do layout que o backend calcula para a grelha de quarteiroes da cena.
 *
 * <p>Vive fora do componente de proposito: e logica pura, e foi exatamente aqui que
 * um bug passou despercebido durante toda a fase 3 -- o frontend recompactava as
 * coordenadas e desfazia o trabalho do CityLayoutCalculator. Fora do React, testa-se.
 */

/**
 * Distancia em unidades de mundo entre os centros de dois quarteiroes vizinhos.
 *
 * <p>Vive aqui e e importada por quem precisa dela. Esteve duplicada em tres ficheiros
 * com dois valores diferentes -- a cidade era desenhada a 22 e as colisoes calculadas
 * a 13 -- o que punha as paredes invisiveis a dezenas de unidades dos edificios a que
 * pertenciam.
 */
export const BLOCK_SCALE = 22;

/**
 * Meia-largura da caixa de colisao de um edificio. A pegada de um edificio e de 10
 * unidades, portanto 6 deixa uma margem pequena para nao se ficar preso a raspar nas
 * paredes.
 */
export const BUILDING_HALF_WIDTH = 6;

/** Minimo de quarteiroes de alcatrao, para uma cidade pequena nao ficar sobre um selo. */
export const MIN_GROUND_BLOCKS = 16;

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
  /** Extensao do chao desenhado, em quarteiroes -- e o que limita para onde se anda. */
  groundW: number;
  groundD: number;
  /** Celulas com edificio, como "gridX,gridZ". As ruinas contam: sao solidas na cena. */
  occupied: Set<string>;
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

  const hosts: PlacedHost[] = (scanData?.hosts ?? []).map(place);
  const ruins: PlacedHost[] = (scanData?.ruins ?? []).map(place);

  return {
    spacing,
    hosts,
    ruins,
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
    groundW: Math.max(layoutW, MIN_GROUND_BLOCKS),
    groundD: Math.max(layoutD, MIN_GROUND_BLOCKS),
    occupied: new Set([...hosts, ...ruins].map(h => `${h.gridX},${h.gridZ}`)),
  };
}

/** Onde o centro de um quarteirao fica no mundo. */
export function worldX(grid: CityGrid, gridX: number): number {
  return (gridX + grid.offsetX) * BLOCK_SCALE;
}

export function worldZ(grid: CityGrid, gridZ: number): number {
  return (gridZ + grid.offsetZ) * BLOCK_SCALE;
}

/**
 * Ate onde se pode andar. O chao e desenhado centrado na origem e com groundW por
 * groundD quarteiroes, portanto os limites sao simetricos -- e nao dependem do offset
 * da cidade, que so move os edificios dentro dele.
 */
export function walkableBounds(grid: CityGrid) {
  const halfX = (grid.groundW / 2) * BLOCK_SCALE;
  const halfZ = (grid.groundD / 2) * BLOCK_SCALE;
  return { minX: -halfX, maxX: halfX, minZ: -halfZ, maxZ: halfZ };
}

/**
 * Ha alguma coisa solida em (x, z)? Sair do alcatrao conta como bater.
 *
 * <p>Testa as quatro celulas em redor do ponto porque a caixa de um edificio pode
 * transbordar da sua propria celula.
 */
export function collidesAt(grid: CityGrid, x: number, z: number): boolean {
  const bounds = walkableBounds(grid);
  if (x < bounds.minX || x > bounds.maxX || z < bounds.minZ || z > bounds.maxZ) {
    return true;
  }

  const gridX = x / BLOCK_SCALE - grid.offsetX;
  const gridZ = z / BLOCK_SCALE - grid.offsetZ;

  for (const gx of [Math.floor(gridX), Math.ceil(gridX)]) {
    for (const gz of [Math.floor(gridZ), Math.ceil(gridZ)]) {
      if (!grid.occupied.has(`${gx},${gz}`)) { continue; }
      if (Math.abs(x - worldX(grid, gx)) < BUILDING_HALF_WIDTH
          && Math.abs(z - worldZ(grid, gz)) < BUILDING_HALF_WIDTH) {
        return true;
      }
    }
  }
  return false;
}

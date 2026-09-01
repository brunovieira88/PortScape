/**
 * Traducao do layout que o backend calcula para a grelha de quarteiroes da cena.
 *
 * <p>Vive fora do componente de proposito: e logica pura, e foi exatamente aqui que
 * um bug passou despercebido durante toda a fase 3 -- o frontend recompactava as
 * coordenadas e desfazia o trabalho do CityLayoutCalculator. Fora do React, testa-se.
 */
import { deviceKindOf } from './buildings/deviceKind';
import { footprintHalfWidth, seedOf } from './buildings/towerForm';

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
 * Folga entre a fachada e a parede invisivel, para nao se ficar preso a raspar.
 *
 * <p>A meia-largura de cada edificio ja nao e uma constante: vem do
 * {@link footprintHalfWidth}, a mesma conta que desenha a planta. Era 6 fixo para
 * todos, herdado de quando todos os edificios eram a mesma caixa de 10x10 -- depois
 * dos arquetipos da fase 4 uma laje chega a 17 unidades de largura, e 38% dos
 * edificios de um /24 ficaram mais largos do que a parede que os protegia. Entrava-se
 * pela fachada dentro.
 */
export const COLLISION_MARGIN = 1;

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
  /**
   * Celulas com edificio, como "gridX,gridZ", e a meia-largura de cada um. As ruinas
   * contam: sao solidas na cena, e bate-se nelas como nas outras.
   */
  occupied: Map<string, number>;
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
    // A meia-largura tem de sair do mesmo numero de portas com que o edificio e
    // desenhado -- incluindo o minimo de 2 que a cena da as ruinas, que de outra
    // forma ficavam com a caixa de um edificio que ninguem ve.
    occupied: new Map([
      ...hosts.map(h => cellFootprint(h, h.portCount ?? 0)),
      ...ruins.map(h => cellFootprint(h, h.portCount || RUIN_MIN_PORTS)),
    ]),
  };
}

/**
 * Portas com que se desenha uma ruina que ja nao responde a nenhuma. Um edificio de
 * altura zero nao e um edificio, e a ruina continua a ter de se ver.
 */
export const RUIN_MIN_PORTS = 2;

function cellFootprint(host: PlacedHost, portCount: number): [string, number] {
  const half = footprintHalfWidth(portCount, seedOf(host.ip ?? ''), deviceKindOf(host.vendor));
  return [`${host.gridX},${host.gridZ}`, half + COLLISION_MARGIN];
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
      const half = grid.occupied.get(`${gx},${gz}`);
      if (half === undefined) { continue; }
      if (Math.abs(x - worldX(grid, gx)) < half && Math.abs(z - worldZ(grid, gz)) < half) {
        return true;
      }
    }
  }
  return false;
}

/**
 * A forma de um edificio: altura, silhueta e pegada no chao.
 *
 * <p>Vive fora do componente que a desenha porque nao e so o desenho que precisa dela.
 * As colisoes tambem precisam de saber a largura real de cada edificio, e a etiqueta
 * precisa da altura -- e sempre que uma destas contas foi duplicada, divergiu: a
 * etiqueta ja flutuou cinco unidades acima do telhado das casas, e a caixa de colisao
 * ja foi calculada a uma escala diferente da que desenhava a cidade.
 *
 * <p>Logica pura, sem React nem three: testa-se.
 */
import type { DeviceKind } from './deviceKind';

/**
 * Semente estavel a partir do IP -- o mesmo host tem sempre o mesmo aspecto, em
 * qualquer scan. Vive aqui com o resto da forma porque quem calcula a pegada para as
 * colisoes tem de chegar exatamente a mesma semente que quem desenha o edificio.
 */
export function seedOf(ip: string): number {
  let hash = 0;
  for (let i = 0; i < ip.length; i++) {
    hash = (hash * 31 + ip.charCodeAt(i)) & 0x7fffffff;
  }
  return hash;
}

/** Andares de uma torre, e altura de cada um. */
export const FLOOR_HEIGHT = 3;
export const MAX_FLOORS = 25;
/** Paredes e telhado de uma casa (portCount <= 3). */
export const HOUSE_HEIGHT = 6;
export const HOUSE_ROOF_HEIGHT = 3;

/**
 * Altura da estrutura, do chao ao topo do telhado. E daqui que sai a posicao da
 * etiqueta por cima do edificio -- calcula-la a parte fazia-a flutuar cinco unidades
 * acima do telhado das casas e ficar por baixo do das torres.
 *
 * <p>Nao inclui os adereços condicionais (antena, drone), que sao decoracao e podem
 * passar acima disto de proposito.
 */
export function buildingHeight(portCount: number, seed = 0, kind: DeviceKind = 'GENERIC'): number {
  if (portCount <= 3 && kind === 'GENERIC') {
    return HOUSE_HEIGHT + HOUSE_ROOF_HEIGHT;
  }
  const floors = Math.max(1, Math.min(portCount, MAX_FLOORS));
  // O coroamento varia com o arquetipo -- uma agulha remata muito acima de uma laje --
  // por isso a altura tem de sair da mesma forma que desenha o edificio.
  return floors * FLOOR_HEIGHT + towerForm(seed, floors, FLOOR_HEIGHT, kind).crown;
}

/** Gerador congruencial: deterministico, e chega perfeitamente para variacao visual. */
export function rngFrom(seed: number) {
  let state = (seed || 1) & 0x7fffffff;
  return () => (state = (state * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;
}

export type TowerStyle = 'SLAB' | 'PRISM' | 'SETBACK' | 'SPIRE';
export type WindowStyle = 'RIBBON' | 'STRIP';

export interface Tier { base: number; width: number; depth: number; height: number; }

export interface TowerForm {
  style: TowerStyle;
  windows: WindowStyle;
  tiers: Tier[];
  /** Rotacao do edificio inteiro, para a cidade nao ficar toda alinhada a esquadro. */
  rotation: number;
  crown: number;
}

/**
 * A forma de uma torre, a partir do IP.
 *
 * <p>Sem isto todas as torres sao a mesma caixa de 10x10 e a cidade le como um asset
 * repetido. Quatro arquetipos com variacao continua dentro de cada um dao skyline:
 * lajes largas e baixas, prismas, torres com recuos, e agulhas finas com antena.
 *
 * <p>A altura total continua a ser {@code andares x FLOOR_HEIGHT} -- e o numero de
 * portas que a manda, e isso e informacao, nao decoracao. O que a forma varia e a
 * <i>planta</i> e a silhueta, nunca a altura.
 */
export function towerForm(seed: number, floors: number, floorHeight: number,
    kind: DeviceKind = 'GENERIC'): TowerForm {
  const rng = rngFrom(seed);
  const H = floors * floorHeight;

  // A agulha so faz sentido acima de uma certa altura -- numa torre baixa le como um
  // chapeu. Tudo o resto esta disponivel em qualquer altura, porque uma rede domestica
  // tem hosts de 4 a 6 portas e e nessa gama que a variedade tem de se ver.
  const roll = rng();
  // Um gateway e sempre uma agulha: numa rede domestica ha tipicamente um so, e a
  // antena no topo torna-o o marco que se procura primeiro ao olhar para a cidade.
  // Um dispositivo embebido e sempre um prisma compacto -- nao tem porte para mais.
  const style: TowerStyle = kind === 'GATEWAY' ? 'SPIRE'
    : kind === 'IOT' ? 'PRISM'
    : floors >= 9
      ? (roll < 0.34 ? 'SETBACK' : roll < 0.60 ? 'SPIRE' : roll < 0.82 ? 'PRISM' : 'SLAB')
      : (roll < 0.36 ? 'SLAB' : roll < 0.68 ? 'PRISM' : 'SETBACK');

  const rotation = (rng() < 0.5 ? 0 : Math.PI / 2) + (rng() - 0.5) * 0.12;
  const wide = 8 + rng() * 6;
  const thin = 4.5 + rng() * 2.5;

  const tiers: Tier[] = [];
  let crown = 2;

  if (style === 'SLAB') {
    tiers.push({ base: 0, width: wide + 3, depth: thin, height: H });
    crown = 1.5;
  } else if (style === 'PRISM') {
    const side = 7 + rng() * 3;
    tiers.push({ base: 0, width: side, depth: side * (0.8 + rng() * 0.4), height: H });
    crown = 2.5;
  } else if (style === 'SETBACK') {
    const cuts = [0.55 + rng() * 0.1, 0.82 + rng() * 0.06];
    const base = 9 + rng() * 3;
    tiers.push({ base: 0, width: base, depth: base * 0.85, height: H * cuts[0] });
    tiers.push({ base: H * cuts[0], width: base * 0.72, depth: base * 0.62, height: H * (cuts[1] - cuts[0]) });
    tiers.push({ base: H * cuts[1], width: base * 0.48, depth: base * 0.42, height: H * (1 - cuts[1]) });
    crown = 3;
  } else {
    // O mastro do gateway e mais alto e mais fino: e decoracao, nao altura -- a
    // altura da estrutura continua a ser floors x floorHeight.
    const base = (kind === 'GATEWAY' ? 5.5 : 7) + rng() * 2;
    tiers.push({ base: 0, width: base, depth: base * 0.9, height: H * 0.78 });
    tiers.push({ base: H * 0.78, width: base * 0.55, depth: base * 0.5, height: H * 0.22 });
    crown = kind === 'GATEWAY' ? 14 + rng() * 4 : 8 + rng() * 6;
  }

  return { style, windows: style === 'SLAB' ? 'RIBBON' : 'STRIP', tiers, rotation, crown };
}

/** A planta de uma casa, que nao passa pelo towerForm: uma caixa de 10x10 sem rotacao. */
export const HOUSE_WIDTH = 10;

/**
 * Meia-largura da pegada do edificio, ja com a rotacao aplicada.
 *
 * <p>E a metade do lado da caixa alinhada aos eixos que envolve o patamar de baixo --
 * que e sempre o mais largo, em qualquer dos arquetipos. E daqui que sai a colisao:
 * uma constante unica para todos os edificios estava errada por construcao, porque uma
 * laje e mais do dobro da largura de uma agulha. Com 6 fixo, 38% dos edificios de um
 * /24 eram mais largos do que a parede que os protegia e entrava-se pela fachada.
 */
export function footprintHalfWidth(portCount: number, seed = 0,
    kind: DeviceKind = 'GENERIC'): number {
  if (portCount <= 3 && kind === 'GENERIC') {
    return HOUSE_WIDTH / 2;
  }
  const floors = Math.max(1, Math.min(portCount, MAX_FLOORS));
  const form = towerForm(seed, floors, FLOOR_HEIGHT, kind);
  const base = form.tiers[0];
  const cos = Math.abs(Math.cos(form.rotation));
  const sin = Math.abs(Math.sin(form.rotation));
  // A caixa rodada e mais larga do que o edificio: um prisma a 45 graus ocupa a
  // diagonal. Toma-se o maior dos dois lados para a colisao ser conservadora.
  return Math.max(base.width * cos + base.depth * sin,
                  base.width * sin + base.depth * cos) / 2;
}

/**
 * Raio de uma circunferencia que envolve a planta do edificio, seja qual for a sua
 * rotacao. E a meia-diagonal do patamar de baixo.
 *
 * <p>E daqui que sai o tamanho dos destaques de host novo/alterado. Estavam em valores
 * fixos escolhidos quando todos os edificios eram a mesma caixa de 10x10: numa laje de
 * 17 de largura a marca passava por <i>dentro</i> do edificio e so se via nas pontas.
 */
export function footprintRadius(portCount: number, seed = 0,
    kind: DeviceKind = 'GENERIC'): number {
  if (portCount <= 3 && kind === 'GENERIC') {
    return Math.SQRT2 * HOUSE_WIDTH / 2;
  }
  const floors = Math.max(1, Math.min(portCount, MAX_FLOORS));
  const base = towerForm(seed, floors, FLOOR_HEIGHT, kind).tiers[0];
  return Math.hypot(base.width, base.depth) / 2;
}

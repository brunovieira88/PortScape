import { describe, it, expect } from 'vitest';
import { footprintHalfWidth, footprintRadius, seedOf, towerForm,
         FLOOR_HEIGHT, MAX_FLOORS } from './towerForm';
import type { DeviceKind } from './deviceKind';

const IPS = Array.from({ length: 254 }, (_, i) => `192.168.1.${i + 1}`);
const KINDS: DeviceKind[] = ['GENERIC', 'GATEWAY', 'IOT'];
const PORT_COUNTS = [0, 1, 3, 4, 8, 12, 25, 40];

/** Todas as combinacoes que a cena pode desenhar. */
function everyBuilding(): { seed: number, ports: number, kind: DeviceKind }[] {
  return IPS.flatMap(ip => PORT_COUNTS.flatMap(ports =>
    KINDS.map(kind => ({ seed: seedOf(ip), ports, kind }))));
}

describe('footprintRadius', () => {

  it('envolve a planta do edificio, em qualquer forma e qualquer rotacao', () => {
    // A regressao: os destaques usavam raios fixos, herdados de quando todos os
    // edificios eram a mesma caixa de 10x10. Numa laje de 17 de largura a marca
    // passava por dentro do edificio e via-se so nas pontas.
    for (const { seed, ports, kind } of everyBuilding()) {
      if (ports <= 3 && kind === 'GENERIC') { continue; }
      const floors = Math.max(1, Math.min(ports, MAX_FLOORS));
      const base = towerForm(seed, floors, FLOOR_HEIGHT, kind).tiers[0];
      const meiaDiagonal = Math.hypot(base.width, base.depth) / 2;

      expect(footprintRadius(ports, seed, kind)).toBeGreaterThanOrEqual(meiaDiagonal);
    }
  });

  it('e sempre pelo menos tao grande como a meia-largura da colisao', () => {
    // Se fosse ao contrario, batia-se numa parede fora da marca que a desenha.
    for (const { seed, ports, kind } of everyBuilding()) {
      expect(footprintRadius(ports, seed, kind))
        .toBeGreaterThanOrEqual(footprintHalfWidth(ports, seed, kind));
    }
  });

  it('cabe no quarteirao: a marca nunca chega ao edificio do lado', () => {
    // Os quarteiroes estao a 22 unidades. A marca de estado e o fio no chao, a
    // (raio + CLEARANCE) com STROKE de espessura -- ver o HostStateMarker.
    const maiorRaio = Math.max(...everyBuilding()
      .map(({ seed, ports, kind }) => footprintRadius(ports, seed, kind)));
    const marcaMaisLarga = maiorRaio + 0.6 + 0.35;
    const fachadaDoVizinho = 22 - maiorRaio;

    expect(marcaMaisLarga).toBeLessThan(fachadaDoVizinho);
  });

  it('e estavel: o mesmo host da sempre a mesma marca', () => {
    const seed = seedOf('192.168.1.73');

    expect(footprintRadius(5, seed)).toBe(footprintRadius(5, seed));
  });
});

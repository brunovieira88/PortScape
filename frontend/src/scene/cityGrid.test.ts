import { describe, it, expect } from 'vitest';
import { buildCityGrid, collidesAt, walkableBounds, worldX, worldZ,
         BLOCK_SCALE } from './cityGrid';
import sampleScan from '../mock/sample-scan.json';

/** Um scan minimo com o layout no formato que o backend serve. */
function scanWith(hosts: any[], layout: any = {}) {
  return {
    layout: { spacing: 4.0, width: 8.0, depth: 4.0, districts: [], ...layout },
    hosts,
    ruins: [],
  };
}

const host = (ip: string, x: number, z: number) => ({ ip, position: { x, z } });

describe('buildCityGrid', () => {

  it('converte a coordenada do backend em quarteirao dividindo pelo spacing', () => {
    const grid = buildCityGrid(scanWith([host('192.168.1.1', 0, 0), host('192.168.1.2', 8, 4)]));

    expect(grid.hosts[0]).toMatchObject({ gridX: 0, gridZ: 0 });
    expect(grid.hosts[1]).toMatchObject({ gridX: 2, gridZ: 1 });
  });

  it('nao volta a compactar: hosts em celulas vizinhas ficam em celulas vizinhas', () => {
    // A regressao que isto trava: dividir por um spacing inflacionado colapsava
    // colunas distintas na mesma celula e obrigava a desempatar por varrimento.
    const hosts = [0, 1, 2, 3, 4, 5].map(i => host(`192.168.1.${i + 1}`, i * 4.0, 0));

    const grid = buildCityGrid(scanWith(hosts));

    expect(grid.hosts.map(h => h.gridX)).toEqual([0, 1, 2, 3, 4, 5]);
  });

  it('nunca poe dois hosts no mesmo quarteirao', () => {
    const grid = buildCityGrid(sampleScan);
    const todos = [...grid.hosts, ...grid.ruins];
    const celulas = new Set(todos.map(h => `${h.gridX},${h.gridZ}`));

    expect(todos.length).toBeGreaterThan(0);
    expect(celulas.size).toBe(todos.length);
  });

  it('poe as ruinas na grelha, como os hosts vivos', () => {
    const grid = buildCityGrid(sampleScan);

    expect(grid.ruins).toHaveLength(1);
    expect(grid.ruins[0].gridX).toBeGreaterThanOrEqual(0);
  });

  it('centra a cidade na origem para a camara nascer no meio', () => {
    const grid = buildCityGrid(scanWith([], { width: 40.0, depth: 20.0 }));

    expect(grid.layoutW).toBe(10);
    expect(grid.layoutD).toBe(5);
    expect(grid.offsetX).toBe(-5);
    expect(grid.offsetZ).toBe(-2.5);
  });

  it('um scan sem layout da uma cidade vazia em vez de rebentar', () => {
    // E o que a listagem devolve: sumarios sem layout nem hosts.
    const grid = buildCityGrid({ id: 'abc', status: 'DONE' });

    expect(grid.hosts).toEqual([]);
    expect(grid.districts).toEqual([]);
    expect(grid.layoutW).toBe(0);
    expect(grid.offsetX).toBe(-0);
  });

  it('um host sem posicao nao rebenta a cena', () => {
    const grid = buildCityGrid(scanWith([{ ip: '192.168.1.1' }]));

    expect(grid.hosts[0]).toMatchObject({ gridX: 0, gridZ: 0 });
  });

  it('traduz os bairros para a placa de chao, em quarteiroes', () => {
    const grid = buildCityGrid(sampleScan);
    const medium = grid.districts.find(d => d.band === 'MEDIUM')!;

    // O bairro MEDIUM do mock tem dois hosts: um bloco de 2x1.
    expect(medium.columns).toBe(2);
    expect(medium.rows).toBe(1);
  });

  it('cada host cai dentro da placa do seu proprio bairro', () => {
    const grid = buildCityGrid(sampleScan);

    for (const h of [...grid.hosts, ...grid.ruins]) {
      const district = grid.districts.find(d => d.band === h.riskBand)!;
      expect(district).toBeDefined();
      expect(h.gridX).toBeGreaterThanOrEqual(district.startX);
      expect(h.gridX).toBeLessThan(district.startX + district.columns);
      expect(h.gridZ).toBeLessThan(district.rows);
    }
  });

  it('os bairros nao se sobrepoem e mantem a ordem de gravidade', () => {
    const grid = buildCityGrid(sampleScan);

    expect(grid.districts.map(d => d.band))
      .toEqual(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']);
    for (let i = 1; i < grid.districts.length; i++) {
      const anterior = grid.districts[i - 1];
      expect(grid.districts[i].startX)
        .toBeGreaterThanOrEqual(anterior.startX + anterior.columns);
    }
  });


  it('marca como ocupadas as celulas dos hosts e das ruinas', () => {
    const grid = buildCityGrid(sampleScan);

    expect(grid.occupied.size).toBe(grid.hosts.length + grid.ruins.length);
    for (const h of [...grid.hosts, ...grid.ruins]) {
      expect(grid.occupied.has(`${h.gridX},${h.gridZ}`)).toBe(true);
    }
  });
});

describe('collidesAt', () => {

  it('a caixa de colisao fica em cima do edificio que ela protege', () => {
    // A regressao: as colisoes eram calculadas a SCALE 13 e centradas pela largura
    // minima, enquanto os edificios eram desenhados a SCALE 22 e centrados pela largura
    // real -- o que punha as paredes ate 73 unidades ao lado do predio.
    const grid = buildCityGrid(sampleScan);

    for (const host of grid.hosts) {
      expect(collidesAt(grid, worldX(grid, host.gridX), worldZ(grid, host.gridZ))).toBe(true);
    }
  });

  it('a rua entre dois edificios e atravessavel', () => {
    const grid = buildCityGrid(sampleScan);
    const [first, second] = grid.hosts;
    const meio = (worldX(grid, first.gridX) + worldX(grid, second.gridX)) / 2;

    // Os dois estao a mais de um quarteirao de distancia, logo ha rua pelo meio.
    expect(Math.abs(first.gridX - second.gridX)).toBeGreaterThan(1);
    expect(collidesAt(grid, meio, worldZ(grid, first.gridZ))).toBe(false);
  });

  it('as ruinas tambem sao solidas', () => {
    const grid = buildCityGrid(sampleScan);
    const ruin = grid.ruins[0];

    expect(collidesAt(grid, worldX(grid, ruin.gridX), worldZ(grid, ruin.gridZ))).toBe(true);
  });

  it('sair do alcatrao conta como bater', () => {
    const grid = buildCityGrid(sampleScan);
    const bounds = walkableBounds(grid);

    expect(collidesAt(grid, bounds.maxX + 1, 0)).toBe(true);
    expect(collidesAt(grid, bounds.minX - 1, 0)).toBe(true);
    expect(collidesAt(grid, 0, bounds.maxZ + 1)).toBe(true);
    expect(collidesAt(grid, 0, bounds.minZ - 1)).toBe(true);
  });

  it('o chao estende-se para la dos edificios de uma cidade pequena', () => {
    const grid = buildCityGrid(sampleScan);

    // Nao se anda dentro de um selo do tamanho exato dos predios.
    expect(grid.groundW).toBeGreaterThanOrEqual(16);
    expect(walkableBounds(grid).maxX).toBeGreaterThan(worldX(grid, grid.layoutW - 1));
  });

  it('encosta-se ao edificio ate a margem da caixa, e nao mais', () => {
    const grid = buildCityGrid(sampleScan);
    const host = grid.hosts[0];
    const centro = worldX(grid, host.gridX);
    const z = worldZ(grid, host.gridZ);
    const meiaLargura = grid.occupied.get(`${host.gridX},${host.gridZ}`)!;

    expect(collidesAt(grid, centro + meiaLargura - 0.1, z)).toBe(true);
    expect(collidesAt(grid, centro + meiaLargura + 0.1, z)).toBe(false);
  });

  it('a caixa acompanha a largura real do edificio, e nao uma constante', () => {
    // A regressao: 6 fixo para todos, herdado de quando todos os edificios eram a
    // mesma caixa de 10x10. Uma laje da fase 4 chega a 8.6 de meia-largura -- 38% dos
    // edificios de um /24 eram mais largos do que a parede que os protegia, e
    // entrava-se pela fachada dentro antes de bater em alguma coisa.
    const grid = buildCityGrid(scanWith([
      { ip: '192.168.1.1', position: { x: 0, z: 0 }, portCount: 12 },
      { ip: '192.168.1.151', position: { x: 8, z: 0 }, portCount: 12 },
    ]));

    const larguras = [...grid.occupied.values()];
    expect(new Set(larguras).size).toBe(2);

    for (const host of grid.hosts) {
      const meiaLargura = grid.occupied.get(`${host.gridX},${host.gridZ}`)!;
      const centro = worldX(grid, host.gridX);
      const z = worldZ(grid, host.gridZ);
      // Bate-se na fachada, seja ela onde for -- e nao no sitio onde ela estaria se
      // todos os edificios fossem iguais.
      expect(collidesAt(grid, centro + meiaLargura - 0.1, z)).toBe(true);
      expect(collidesAt(grid, centro + meiaLargura + 0.1, z)).toBe(false);
    }
  });
});

describe('collidesAt (limites do mundo)', () => {

  it('uma cidade sem hosts e toda atravessavel', () => {
    const grid = buildCityGrid({ layout: { spacing: 4.0, width: 0, depth: 0, districts: [] } });

    expect(collidesAt(grid, 0, 0)).toBe(false);
    expect(walkableBounds(grid).maxX).toBe(8 * BLOCK_SCALE);
  });
});

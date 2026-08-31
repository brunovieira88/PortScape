import { describe, it, expect } from 'vitest';
import { buildCityGrid } from './cityGrid';
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
});

import { describe, expect, it } from 'vitest';
import { asScan, type JsonScan } from './fixture';
import { demoScan } from './demoScan';

/**
 * A guarda existe por causa de uma deriva real: as fixtures usaram durante meses cinco
 * códigos de risco que o backend nunca emitiu, e ninguém deu por isso porque o campo
 * era `string` dos dois lados. O `tsc` continua sem poder apanhá-lo — o TypeScript
 * alarga as strings de um módulo JSON — por isso a verificação é em runtime, e estes
 * testes são o que garante que ela não é decorativa.
 */
function scanWith(code: string): JsonScan {
  return {
    id: 'test', target: '192.168.1.0/24', status: 'DONE',
    createdAt: '2026-01-01T00:00:00Z', hostsUp: 1,
    hosts: [{
      ip: '192.168.1.10', portCount: 0,
      riskReasons: [{ code, description: 'seja o que for', points: 10 }],
    }],
  };
}

describe('asScan', () => {
  it('aceita um codigo que o backend emite', () => {
    expect(asScan(scanWith('OPEN_PORT')).hosts?.[0].riskReasons?.[0].code).toBe('OPEN_PORT');
  });

  it('recusa um codigo que o backend nao emite, e diz qual e onde', () => {
    // Um dos cinco que a fixture da demo chegou mesmo a ter.
    expect(() => asScan(scanWith('HIGH_RISK_PORT')))
      .toThrowError(/HIGH_RISK_PORT.*192\.168\.1\.10/s);
  });

  it('a fixture da demo passa na guarda -- e o que a torna util', () => {
    // Se isto falhar, a demo publica esta a mostrar rotulos que o backend nunca produz.
    expect(demoScan.hosts?.length).toBeGreaterThan(0);
  });
});

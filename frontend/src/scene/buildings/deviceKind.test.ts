import { describe, it, expect } from 'vitest';
import { deviceKindOf } from './deviceKind';

describe('deviceKindOf', () => {

  it('reconhece equipamento de rede pelo fabricante', () => {
    expect(deviceKindOf('Ubiquiti Networks Inc.')).toBe('GATEWAY');
    expect(deviceKindOf('MikroTik')).toBe('GATEWAY');
    expect(deviceKindOf('Sagemcom Broadband SAS')).toBe('GATEWAY');
  });

  it('reconhece dispositivos embebidos', () => {
    expect(deviceKindOf('Espressif Inc.')).toBe('IOT');
    expect(deviceKindOf('Raspberry Pi Trading Ltd')).toBe('IOT');
    expect(deviceKindOf('Sonos, Inc.')).toBe('IOT');
  });

  it('nao se importa com maiusculas nem com o resto do nome', () => {
    expect(deviceKindOf('ESPRESSIF INC.')).toBe('IOT');
    expect(deviceKindOf('  Ubiquiti Networks  ')).toBe('GATEWAY');
  });

  it('um fabricante desconhecido nao e adivinhado', () => {
    // Melhor cair no generico do que inventar: uma tabela que tenta apanhar tudo
    // envelhece mal e da falsos positivos.
    expect(deviceKindOf('Apple, Inc.')).toBe('GENERIC');
    expect(deviceKindOf('Alguma Empresa Nova Lda')).toBe('GENERIC');
  });

  it('sem MAC nao ha fabricante, e a forma volta a sair das portas', () => {
    // E o caso da propria maquina que corre o scan, e de qualquer scan sem privilegios.
    expect(deviceKindOf(null)).toBe('GENERIC');
    expect(deviceKindOf(undefined)).toBe('GENERIC');
    expect(deviceKindOf('')).toBe('GENERIC');
  });

  it('na duvida entre rede e IoT, ganha a rede', () => {
    // A TP-Link faz routers e lampadas; marcar um router como lampada e o erro pior.
    expect(deviceKindOf('TP-Link Technologies Co.Ltd.')).toBe('GATEWAY');
  });
});

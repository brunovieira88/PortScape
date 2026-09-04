// @vitest-environment happy-dom
//
// So os testes que precisam de DOM e que pagam o ambiente. Os testes de logica pura --
// cityGrid, towerForm, cameraIntro -- continuam a correr em node, que e mais rapido.
import { act, renderHook } from '@testing-library/react';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { demoScan } from '../mock/demoScan';
import * as client from './client';
import type { Scan } from './types';
import { useScanJob } from './useScanJob';

// O ApiError e mantido: o hook faz `instanceof ApiError` para decidir se mostra a
// explicacao do backend ou o texto generico, e um duplo mockado nunca passaria nesse
// teste -- so as chamadas de rede e que sao substituidas.
vi.mock('./client', async importOriginal => ({
  ...await importOriginal<typeof client>(),
  startScan: vi.fn(),
  cancelScan: vi.fn(),
  getScan: vi.fn(),
  listScans: vi.fn(),
}));

const startScan = vi.mocked(client.startScan);
const cancelScan = vi.mocked(client.cancelScan);
const getScan = vi.mocked(client.getScan);
const listScans = vi.mocked(client.listScans);

function scanOf(id: string, overrides: Partial<Scan> = {}): Scan {
  return {
    id,
    target: '192.168.1.0/24',
    status: 'DONE',
    createdAt: '2026-01-01T00:00:00Z',
    hostsUp: 0,
    progress: 100,
    hosts: [],
    ruins: [],
    ...overrides,
  };
}

/** Deixa correr o tempo com o React a par das actualizacoes que isso provoca. */
async function advance(ms: number) {
  await act(async () => { await vi.advanceTimersByTimeAsync(ms); });
}

const POLL = 1500;
const REVEAL = 600;

beforeEach(() => {
  vi.useFakeTimers();
  vi.clearAllMocks();
  // O arranque pergunta sempre pelo historico. Sem scans guardados fica-se na cidade
  // de exemplo, que e o caso base de quem abre a app pela primeira vez.
  listScans.mockResolvedValue([]);
});

afterEach(() => {
  vi.useRealTimers();
});

describe('useScanJob', () => {

  it('mostra a cidade nova quando o scan termina, e so depois da barra chegar ao fim', async () => {
    startScan.mockResolvedValue(scanOf('s1', { status: 'PENDING', progress: 0 }));
    getScan
      .mockResolvedValueOnce(scanOf('s1', { status: 'RUNNING', progress: 40 }))
      .mockResolvedValueOnce(scanOf('s1', { status: 'DONE', progress: 100 }));

    const onScanShown = vi.fn();
    const { result } = renderHook(() => useScanJob({ onScanShown }));

    await act(async () => { await result.current.startScan('192.168.1.0/24'); });
    expect(result.current.isScanning).toBe(true);

    await advance(POLL);
    // O progresso e o do backend, e nao uma curva inventada no cliente.
    expect(result.current.progress).toBe(40);
    expect(result.current.scanStatus).toBe('SCAN STATUS: RUNNING');

    await advance(POLL);
    expect(result.current.progress).toBe(100);
    // A cidade ainda nao mudou: falta o atraso que deixa a barra ver-se nos 100%.
    expect(result.current.scanData.id).toBe(demoScan.id);
    expect(onScanShown).not.toHaveBeenCalled();

    await advance(REVEAL);
    expect(result.current.scanData.id).toBe('s1');
    expect(result.current.isScanning).toBe(false);
    expect(onScanShown).toHaveBeenCalledTimes(1);
  });

  it('um scan falhado mostra a explicacao do backend e para de sondar', async () => {
    startScan.mockResolvedValue(scanOf('s1', { status: 'PENDING' }));
    getScan.mockResolvedValue(scanOf('s1', {
      status: 'FAILED',
      error: { code: 'NMAP_PRIVILEGE', message: 'nmap needs root for -sS' },
    }));

    const { result } = renderHook(() => useScanJob());
    await act(async () => { await result.current.startScan(); });
    await advance(POLL);

    expect(result.current.scanError).toBe('nmap needs root for -sS');
    expect(result.current.isScanning).toBe(false);
    expect(result.current.scanData.id).toBe(demoScan.id);

    // E para mesmo: sem isto, sondava um scan morto para sempre.
    const callsSoFar = getScan.mock.calls.length;
    await advance(POLL * 5);
    expect(getScan).toHaveBeenCalledTimes(callsSoFar);
  });

  it('o backend explica-se quando o scan nem chega a arrancar', async () => {
    startScan.mockRejectedValue(
      new client.ApiError('Target 8.8.8.8 is not a private network', 'INVALID_TARGET', 400));

    const { result } = renderHook(() => useScanJob());
    await act(async () => { await result.current.startScan('8.8.8.8'); });

    expect(result.current.scanError).toBe('Target 8.8.8.8 is not a private network');
    expect(result.current.isScanning).toBe(false);
    expect(result.current.scanStatus).toBe('');
  });

  it('um scan aberto a meio de outro nao e roubado pelo scan em curso', async () => {
    // A regressao: o utilizador clica num scan do historico enquanto outro decorre, e
    // meio segundo depois o scan em curso pintava o ecra por cima do que ele escolheu.
    startScan.mockResolvedValue(scanOf('novo', { status: 'PENDING' }));
    getScan.mockImplementation(async id =>
      id === 'antigo' ? scanOf('antigo') : scanOf('novo', { status: 'DONE' }));

    const { result } = renderHook(() => useScanJob());
    await act(async () => { await result.current.startScan(); });

    await act(async () => { await result.current.loadScan('antigo'); });
    expect(result.current.scanData.id).toBe('antigo');

    // O scan em curso termina agora. Quem manda no ecra e a ultima escolha do
    // utilizador, e nao quem chega por ultimo.
    await advance(POLL + REVEAL);
    expect(result.current.scanData.id).toBe('antigo');
  });

  it('cancelar para a sondagem e nao mexe na cidade', async () => {
    startScan.mockResolvedValue(scanOf('s1', { status: 'PENDING' }));
    getScan.mockResolvedValue(scanOf('s1', { status: 'RUNNING', progress: 30 }));
    cancelScan.mockResolvedValue(scanOf('s1', { status: 'CANCELLED', progress: 0 }));

    const { result } = renderHook(() => useScanJob());
    await act(async () => { await result.current.startScan(); });
    await advance(POLL);

    await act(async () => { await result.current.cancelScan(); });

    expect(cancelScan).toHaveBeenCalledWith('s1');
    expect(result.current.isScanning).toBe(false);
    // Nao e um erro: foi o utilizador que pediu.
    expect(result.current.scanError).toBeNull();
    expect(result.current.scanData.id).toBe(demoScan.id);

    const callsSoFar = getScan.mock.calls.length;
    await advance(POLL * 5);
    expect(getScan).toHaveBeenCalledTimes(callsSoFar);
  });

  it('um scan que aparece CANCELLED na sondagem tambem e um fim', async () => {
    // O cancelamento pode vir de fora deste ecra. Sem tratar o estado, sondava-se um
    // scan parado para sempre -- o teste era `!== DONE && !== FAILED`.
    startScan.mockResolvedValue(scanOf('s1', { status: 'PENDING' }));
    getScan.mockResolvedValue(scanOf('s1', { status: 'CANCELLED', progress: 0 }));

    const { result } = renderHook(() => useScanJob());
    await act(async () => { await result.current.startScan(); });
    await advance(POLL);

    expect(result.current.isScanning).toBe(false);
    expect(result.current.scanError).toBeNull();

    const callsSoFar = getScan.mock.calls.length;
    await advance(POLL * 5);
    expect(getScan).toHaveBeenCalledTimes(callsSoFar);
  });

  it('um scan que acabou entre a sondagem e o clique nao da erro nenhum', async () => {
    // 409: quem sonda de 1500 em 1500 ms pode sempre carregar em cancelar no instante
    // exacto em que o scan termina. Nao ha nada a corrigir, e um erro vermelho por
    // isso seria assustar sem motivo.
    startScan.mockResolvedValue(scanOf('s1', { status: 'PENDING' }));
    getScan.mockResolvedValue(scanOf('s1', { status: 'RUNNING' }));
    cancelScan.mockRejectedValue(
      new client.ApiError('O scan ja terminou (DONE).', 'SCAN_NOT_CANCELLABLE', 409));

    const { result } = renderHook(() => useScanJob());
    await act(async () => { await result.current.startScan(); });
    await advance(POLL);
    await act(async () => { await result.current.cancelScan(); });

    expect(result.current.scanError).toBeNull();
    expect(result.current.isScanning).toBe(false);
  });

  it('uma falha a cancelar diz-se', async () => {
    startScan.mockResolvedValue(scanOf('s1', { status: 'PENDING' }));
    getScan.mockResolvedValue(scanOf('s1', { status: 'RUNNING' }));
    cancelScan.mockRejectedValue(new client.ApiError('Connection refused', null, 503));

    const { result } = renderHook(() => useScanJob());
    await act(async () => { await result.current.startScan(); });
    await advance(POLL);
    await act(async () => { await result.current.cancelScan(); });

    expect(result.current.scanError).toBe('Connection refused');
  });

  it('sem scan a decorrer, cancelar nao faz pedido nenhum', async () => {
    const { result } = renderHook(() => useScanJob());
    await act(async () => { await result.current.cancelScan(); });

    expect(cancelScan).not.toHaveBeenCalled();
  });

  it('desmontar mata a sondagem', async () => {
    startScan.mockResolvedValue(scanOf('s1', { status: 'PENDING' }));
    getScan.mockResolvedValue(scanOf('s1', { status: 'RUNNING', progress: 10 }));

    const { result, unmount } = renderHook(() => useScanJob());
    await act(async () => { await result.current.startScan(); });
    await advance(POLL);

    const callsBefore = getScan.mock.calls.length;
    unmount();
    await advance(POLL * 5);

    // Sem a limpeza, ficava um setInterval a bater no backend depois de a app sair.
    expect(getScan).toHaveBeenCalledTimes(callsBefore);
  });

  it('apagar o scan que esta no ecra volta a cidade de exemplo', async () => {
    getScan.mockResolvedValue(scanOf('s1'));

    const onScanForgotten = vi.fn();
    const { result } = renderHook(() => useScanJob({ onScanForgotten }));
    await act(async () => { await result.current.loadScan('s1'); });
    expect(result.current.scanData.id).toBe('s1');

    act(() => { result.current.forgetScan('s1'); });

    expect(result.current.scanData.id).toBe(demoScan.id);
    expect(onScanForgotten).toHaveBeenCalledTimes(1);
  });

  it('apagar um scan que nao esta no ecra nao mexe na cidade', async () => {
    getScan.mockResolvedValue(scanOf('s1'));

    const onScanForgotten = vi.fn();
    const { result } = renderHook(() => useScanJob({ onScanForgotten }));
    await act(async () => { await result.current.loadScan('s1'); });

    act(() => { result.current.forgetScan('outro'); });

    expect(result.current.scanData.id).toBe('s1');
    expect(onScanForgotten).not.toHaveBeenCalled();
  });

  it('abre no scan mais recente que houver guardado', async () => {
    listScans.mockResolvedValue([scanOf('recente'), scanOf('velho')]);
    getScan.mockResolvedValue(scanOf('recente'));

    const { result } = renderHook(() => useScanJob());
    await act(async () => { await vi.advanceTimersByTimeAsync(0); });

    expect(result.current.scanData.id).toBe('recente');
    expect(result.current.isBooting).toBe(false);
  });

  it('sem backend fica-se na cidade de exemplo, e nao num ecra vazio', async () => {
    listScans.mockRejectedValue(new TypeError('Failed to fetch'));

    const { result } = renderHook(() => useScanJob());
    await act(async () => { await vi.advanceTimersByTimeAsync(0); });

    expect(result.current.scanData.id).toBe(demoScan.id);
    expect(result.current.isBooting).toBe(false);
  });
});

// @vitest-environment happy-dom
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Host, Scan } from '../api/types';
import { DeviceListPanel } from './DeviceListPanel';

function scanOf(hosts: Host[], ruins: Host[] = []): Scan {
  return {
    id: 's1', target: '192.168.1.0/24', status: 'DONE',
    createdAt: '2026-01-01T00:00:00Z', hostsUp: hosts.length,
    hosts, ruins,
  };
}

// A limpeza automatica do Testing Library so acontece com os globals do Vitest
// ligados. Sem isto, cada render acumulava no mesmo documento e os testes viam os
// hosts de todos os testes anteriores.
afterEach(cleanup);

const host = (ip: string, overrides: Partial<Host> = {}): Host =>
  ({ ip, portCount: 0, riskBand: 'LOW', ...overrides });

/** As etiquetas que batem certo com o padrao, pela ordem em que aparecem no ecra. */
function labelsOf(pattern: RegExp): string[] {
  return screen.queryAllByText(pattern).map(el => el.textContent ?? '');
}

/** Os IPs da lista de activos, pela ordem em que aparecem no ecra. */
function listedIps(): string[] {
  return labelsOf(/^\d+\.\d+\.\d+\.\d+$/);
}

function renderPanel(scan: Scan, onOpenDetails = vi.fn()) {
  render(<DeviceListPanel scanData={scan} onOpenDetails={onOpenDetails}
                          isOpen={true} onToggle={vi.fn()} />);
  return onOpenDetails;
}

describe('DeviceListPanel', () => {

  it('ordena por endereco e nao por texto', () => {
    // Em ordem alfabetica o .100 vem antes do .2, que nao e a ordem por que ninguem
    // le uma rede.
    renderPanel(scanOf([host('192.168.1.100'), host('192.168.1.2'), host('192.168.1.20')]));

    expect(listedIps()).toEqual(['192.168.1.2', '192.168.1.20', '192.168.1.100']);
  });

  it('um endereco que nao se consegue ler vai para o fim, e nao para o meio', () => {
    renderPanel(scanOf([host('fe80::1'), host('192.168.1.5'), host('192.168.1.1')]));

    // O IPv6 nao tem chave numerica; em vez de cair a meio da rede por acaso da
    // ordenacao, fica no fim.
    expect(labelsOf(/^(\d+\.|fe80)/)).toEqual(['192.168.1.1', '192.168.1.5', 'fe80::1']);
  });

  it('desligar uma faixa esconde os hosts dessa faixa e mais nenhum', async () => {
    renderPanel(scanOf([
      host('192.168.1.1', { riskBand: 'CRITICAL' }),
      host('192.168.1.2', { riskBand: 'LOW' }),
    ]));
    expect(listedIps()).toHaveLength(2);

    await userEvent.click(screen.getByRole('button', { name: 'CRITICAL' }));

    expect(listedIps()).toEqual(['192.168.1.2']);
  });

  it('comeca com todas as faixas ligadas', () => {
    // Um filtro que esconde hosts por defeito faz alguem "perder" uma maquina critica
    // so porque abriu o painel depois de mexer noutra faixa.
    renderPanel(scanOf([
      host('192.168.1.1', { riskBand: 'CRITICAL' }),
      host('192.168.1.2', { riskBand: 'HIGH' }),
      host('192.168.1.3', { riskBand: 'MEDIUM' }),
      host('192.168.1.4', { riskBand: 'LOW' }),
      host('192.168.1.5', { riskBand: 'UNKNOWN' }),
    ]));

    expect(listedIps()).toHaveLength(5);
  });

  it('esconder tudo diz que foi o filtro, e nao que a rede esta vazia', async () => {
    renderPanel(scanOf([host('192.168.1.1', { riskBand: 'LOW' })]));

    await userEvent.click(screen.getByRole('button', { name: 'LOW' }));

    expect(screen.getByText('NO HOSTS MATCH THE SELECTED FILTERS')).toBeDefined();
  });

  it('as ruinas ficam na sua propria seccao e escapam ao filtro de risco', async () => {
    renderPanel(scanOf(
      [host('192.168.1.1', { riskBand: 'LOW' })],
      [host('192.168.1.99', { riskBand: 'LOW', change: 'DISAPPEARED' })]));

    expect(screen.getByText('Offline Relics')).toBeDefined();

    // Numa auditoria, uma maquina que desapareceu e tao relevante como uma nova: o
    // filtro de risco dos activos nao a pode fazer sumir.
    await userEvent.click(screen.getByRole('button', { name: 'LOW' }));

    expect(screen.getByText('NO HOSTS MATCH THE SELECTED FILTERS')).toBeDefined();
    expect(screen.getByText('192.168.1.99')).toBeDefined();
  });

  it('clicar num dispositivo abre os detalhes desse dispositivo', async () => {
    const onOpenDetails = renderPanel(scanOf([host('192.168.1.7', { vendor: 'Synology' })]));

    await userEvent.click(screen.getByText('192.168.1.7'));

    expect(onOpenDetails).toHaveBeenCalledTimes(1);
    expect(onOpenDetails.mock.calls[0][0]).toMatchObject({ ip: '192.168.1.7' });
  });

  it('mostra o hostname sem o sufixo da rede local, ou o fabricante quando nao ha', () => {
    renderPanel(scanOf([
      host('192.168.1.1', { hostname: 'nas.home' }),
      host('192.168.1.2', { vendor: 'Espressif Inc.' }),
      host('192.168.1.3'),
    ]));

    expect(screen.getByText('nas')).toBeDefined();
    expect(screen.getByText('Espressif Inc.')).toBeDefined();
    expect(screen.getByText('Unknown Host')).toBeDefined();
  });

  it('um scan da listagem, sem hosts, nao rebenta o painel', () => {
    renderPanel(scanOf([]));

    expect(screen.getByText('Active Targets')).toBeDefined();
    expect(listedIps()).toEqual([]);
  });
});

describe('cabecalho', () => {
  it('diz que a subnet foi detectada quando o scan nao trouxe alvo', () => {
    render(<DeviceListPanel
      scanData={{ ...scanOf([]), target: '' }}
      isOpen={true} onToggle={vi.fn()} />);

    expect(screen.getByText(/AUTO-DETECTED/)).toBeDefined();
  });
});

// @vitest-environment happy-dom
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as client from '../api/client';
import type { Scan } from '../api/types';
import { HistoryPanel } from './HistoryPanel';

vi.mock('../api/client', async importOriginal => ({
  ...await importOriginal<typeof client>(),
  listScans: vi.fn(),
  deleteScan: vi.fn(),
}));

const listScans = vi.mocked(client.listScans);
const deleteScan = vi.mocked(client.deleteScan);

/** O atraso da animacao de demolicao, antes de o scan sair mesmo. */
const DEMOLITION_MS = 800;

function scanOf(id: string, overrides: Partial<Scan> = {}): Scan {
  return {
    id, target: '192.168.1.0/24', status: 'DONE',
    createdAt: '2026-01-01T10:00:00Z', hostsUp: 4,
    ...overrides,
  };
}

function renderPanel(props: Partial<Parameters<typeof HistoryPanel>[0]> = {}) {
  const onSelectScan = vi.fn();
  const onScanDeleted = vi.fn();
  render(<HistoryPanel onSelectScan={onSelectScan} onScanDeleted={onScanDeleted}
                       isOpen={true} onToggle={vi.fn()} {...props} />);
  return { onSelectScan, onScanDeleted };
}

/** O botao esticado que cobre o cartao de um scan. */
function scanCard() {
  return screen.getByRole('button', { name: /^Open scan from/ });
}

beforeEach(() => {
  vi.clearAllMocks();
  listScans.mockResolvedValue([scanOf('s1')]);
  deleteScan.mockResolvedValue(undefined);
});

afterEach(cleanup);

describe('HistoryPanel', () => {

  it('chega-se a um scan por teclado e abre-se com Enter', async () => {
    const { onSelectScan } = renderPanel();
    await waitFor(() => scanCard());

    // O cartao era um `div onClick`: o Tab saltava a lista inteira e nao havia forma
    // de abrir um scan sem rato.
    scanCard().focus();
    await userEvent.keyboard('{Enter}');

    expect(onSelectScan).toHaveBeenCalledWith('s1');
  });

  it('apagar um scan nao o abre pelo caminho', async () => {
    // O botao de apagar esta por dentro da area do cartao, e o que o mantem clicavel
    // por cima do botao esticado e a ordem de empilhamento (z-20 contra z-10).
    //
    // Este teste NAO cobre essa parte: o happy-dom nao faz layout, portanto um clique
    // vai direito ao elemento escolhido e nao ao que estaria mesmo por cima no ecra.
    // O que fica coberto e o resto -- o cartao ja nao tem handler proprio, e apagar
    // continua a pedir confirmacao em vez de carregar o scan. A sobreposicao verifica-se
    // com o rato, no browser.
    const { onSelectScan } = renderPanel();
    await waitFor(() => scanCard());

    await userEvent.click(screen.getByRole('button', { name: /^Delete scan from/ }));

    expect(onSelectScan).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /^Confirm deleting/ })).toBeDefined();
  });

  it('apagar pede confirmacao, e so depois apaga', async () => {
    const { onScanDeleted } = renderPanel();
    await waitFor(() => scanCard());

    await userEvent.click(screen.getByRole('button', { name: /^Delete scan from/ }));

    // Nada foi apagado so por carregar no caixote: primeiro pergunta-se.
    expect(deleteScan).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: /^Confirm deleting/ }));

    // O pedido so parte no fim da animacao de demolicao.
    await waitFor(() => expect(deleteScan).toHaveBeenCalledWith('s1'),
                  { timeout: DEMOLITION_MS * 3 });
    await waitFor(() => expect(onScanDeleted).toHaveBeenCalledWith('s1'));
  });

  it('desistir de apagar deixa o scan onde estava', async () => {
    renderPanel();
    await waitFor(() => scanCard());

    await userEvent.click(screen.getByRole('button', { name: /^Delete scan from/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Keep the scan' }));

    expect(deleteScan).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /^Delete scan from/ })).toBeDefined();
  });

  it('um backend em baixo diz-se, em vez de parecer um historico vazio', async () => {
    listScans.mockRejectedValue(new client.ApiError('Connection refused', null, 503));
    renderPanel();

    // Como alerta: e uma interrupcao legitima, ao contrario do progresso de um scan.
    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toContain('Connection refused');
  });

  it('com o painel fechado, o conteudo sai do alcance do Tab', async () => {
    renderPanel({ isOpen: false });
    await waitFor(() => expect(listScans).toHaveBeenCalled());

    const toggle = screen.getByRole('button', { name: /history/i });
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(document.getElementById('scan-history-content')?.hasAttribute('inert')).toBe(true);
  });
});

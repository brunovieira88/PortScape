// @vitest-environment happy-dom
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Host } from '../api/types';
import { HostDetailsModal } from './HostDetailsModal';

// Ver a nota no DeviceListPanel.test: sem globals, a limpeza e por conta de quem
// escreve o teste.
afterEach(cleanup);

function hostOf(overrides: Partial<Host> = {}): Host {
  return { ip: '192.168.1.10', portCount: 0, ...overrides };
}

describe('HostDetailsModal', () => {

  it('mostra as portas e as razoes do score, que e o que traz o utilizador aqui', async () => {
    render(<HostDetailsModal host={hostOf({
      hostname: 'nas.home',
      vendor: 'Synology',
      riskScore: 72,
      riskBand: 'HIGH',
      portCount: 2,
      ports: [
        { number: 23, protocol: 'tcp', state: 'open', service: 'telnet' },
        { number: 445, protocol: 'tcp', state: 'open', service: 'microsoft-ds' },
      ],
      riskReasons: [
        { code: 'HIGH_RISK_PORT', description: 'Telnet exposed (23)', points: 40 },
      ],
    })} onClose={vi.fn()} />);

    expect(screen.getByText('192.168.1.10')).toBeDefined();
    expect(screen.getByText('72')).toBeDefined();
    expect(screen.getByText('23')).toBeDefined();
    expect(screen.getByText('telnet')).toBeDefined();
    expect(screen.getByText('Telnet exposed (23)')).toBeDefined();
    // O sufixo da rede local so faz ruido numa lista onde todos o tem.
    expect(screen.getByText('nas')).toBeDefined();
  });

  it('um host sem risco nem portas diz que nao ha, em vez de duas caixas vazias', () => {
    render(<HostDetailsModal host={hostOf()} onClose={vi.fn()} />);

    expect(screen.getByText('No significant risks detected.')).toBeDefined();
    expect(screen.getByText('No open ports detected.')).toBeDefined();
    expect(screen.getByText('UNKNOWN')).toBeDefined();
  });

  it('fecha com a tecla Escape', async () => {
    const onClose = vi.fn();
    render(<HostDetailsModal host={hostOf()} onClose={onClose} />);

    await userEvent.keyboard('{Escape}');

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('anuncia-se como dialogo, com o IP por nome', () => {
    render(<HostDetailsModal host={hostOf({ ip: '192.168.1.42' })} onClose={vi.fn()} />);

    expect(screen.getByRole('dialog', { name: '192.168.1.42' })).toBeDefined();
  });

  it('o foco entra no dialogo ao abrir', () => {
    render(<HostDetailsModal host={hostOf()} onClose={vi.fn()} onTeleport={vi.fn()} />);

    const dialog = screen.getByRole('dialog');
    expect(dialog.contains(document.activeElement)).toBe(true);
  });

  it('o Tab nao sai do dialogo', async () => {
    // Sem isto, o Tab continuava a passear pela pagina por baixo -- que esta tapada
    // mas nao desaparecida -- e so se voltava ao dialogo depois de a percorrer toda.
    render(<HostDetailsModal host={hostOf()} onClose={vi.fn()} onTeleport={vi.fn()} />);
    const dialog = screen.getByRole('dialog');

    for (let i = 0; i < 6; i++) {
      await userEvent.tab();
      expect(dialog.contains(document.activeElement)).toBe(true);
    }

    await userEvent.tab({ shift: true });
    expect(dialog.contains(document.activeElement)).toBe(true);
  });

  it('ao fechar, o foco volta ao sitio de onde veio', async () => {
    // O cartao do dispositivo no inventario e quem abre isto. Sem restaurar o foco,
    // fecha-se o dialogo e aterra-se no principio da pagina.
    const origin = document.createElement('button');
    origin.textContent = 'Open details';
    document.body.appendChild(origin);
    origin.focus();

    const { unmount } = render(<HostDetailsModal host={hostOf()} onClose={vi.fn()} />);
    expect(document.activeElement).not.toBe(origin);

    unmount();

    expect(document.activeElement).toBe(origin);
    origin.remove();
  });

  it('o botao de teleporte so aparece quando ha para onde ir', async () => {
    const onTeleport = vi.fn();
    const { rerender } = render(
      <HostDetailsModal host={hostOf()} onClose={vi.fn()} onTeleport={onTeleport} />);

    await userEvent.click(screen.getByRole('button', { name: /go to/i }));
    expect(onTeleport).toHaveBeenCalledTimes(1);

    // Uma ruina que ja nao esta na cidade nao leva callback nenhum.
    rerender(<HostDetailsModal host={hostOf()} onClose={vi.fn()} />);
    expect(screen.queryByRole('button', { name: /go to/i })).toBeNull();
  });
});

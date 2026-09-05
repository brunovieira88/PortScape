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
        { code: 'OPEN_PORT', description: 'Telnet exposed (23)', points: 40 },
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


  it('cada porta diz o que la corre -- sem versao nao ha CVE que se interprete', () => {
    render(<HostDetailsModal host={hostOf({
      portCount: 1,
      ports: [{ number: 22, protocol: 'tcp', state: 'open', service: 'ssh',
                product: 'OpenSSH', version: '9.3' }],
    })} onClose={vi.fn()} />);

    expect(screen.getByText(/OpenSSH 9.3/)).toBeDefined();
  });

  it('a porta abre para as falhas conhecidas, com o vector traduzido e o link para o NVD', async () => {
    const user = userEvent.setup();
    render(<HostDetailsModal host={hostOf({
      portCount: 1,
      ports: [{
        number: 445, protocol: 'tcp', state: 'open', service: 'microsoft-ds',
        product: 'Samba smbd', version: '4.6.2', cveTotal: 12,
        cves: [{
          id: 'CVE-2017-7494', cvssScore: 9.8, severity: 'CRITICAL',
          vector: 'CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H',
          description: 'Samba allows remote code execution, aka SambaCry.',
          url: 'https://nvd.nist.gov/vuln/detail/CVE-2017-7494',
          kev: { dateAdded: '2023-03-30', knownRansomwareUse: true,
                 vulnerabilityName: 'Samba Remote Code Execution Vulnerability',
                 requiredAction: 'Apply updates per vendor instructions.' },
        }],
      }],
    })} onClose={vi.fn()} />);

    // Fechada, a porta anuncia quantas falhas tem sem as listar.
    expect(screen.getByText('12 CVES')).toBeDefined();
    expect(screen.queryByText('CVE-2017-7494')).toBeNull();

    await user.click(screen.getByRole('button', { name: /microsoft-ds/i }));

    const link = screen.getByRole('link', { name: 'CVE-2017-7494' });
    expect(link.getAttribute('href')).toBe('https://nvd.nist.gov/vuln/detail/CVE-2017-7494');
    expect(screen.getByText('9.8 CRITICAL')).toBeDefined();
    // O vector deixa de ser jargao e passa a dizer porque e que 9.8 e 9.8.
    expect(screen.getByText('reachable from the network')).toBeDefined();
    expect(screen.getByText('no account needed')).toBeDefined();
    // Truncado: mostrar 1 sem dizer que eram 12 seria mentir por omissao.
    expect(screen.getByText(/Showing the 1 highest-scoring of 12 known CVEs/)).toBeDefined();
  });

  it('um CVE em exploracao activa diz-se, e diz-se que e ransomware', async () => {
    const user = userEvent.setup();
    render(<HostDetailsModal host={hostOf({
      portCount: 1,
      ports: [{
        number: 3389, protocol: 'tcp', state: 'open', service: 'ms-wbt-server',
        cveTotal: 1,
        cves: [{
          id: 'CVE-2019-0708', cvssScore: 9.8, severity: 'CRITICAL',
          kev: { dateAdded: '2021-11-03', knownRansomwareUse: true,
                 vulnerabilityName: 'BlueKeep', requiredAction: 'Apply updates per vendor instructions.' },
        }],
      }],
    })} onClose={vi.fn()} />);

    // O aviso tem de ser legivel com a porta fechada: e o sinal mais forte que ha.
    expect(screen.getByText('Exploited')).toBeDefined();

    await user.click(screen.getByRole('button', { name: /ms-wbt-server/i }));

    expect(screen.getByText(/Actively exploited/)).toBeDefined();
    expect(screen.getByText(/ransomware/)).toBeDefined();
    // A CISA nao diz so que esta a ser explorado -- diz o que fazer a seguir.
    expect(screen.getByText(/Apply updates per vendor instructions/)).toBeDefined();
  });

  it('uma porta sem CVEs nao vira botao -- nao ha nada para abrir', () => {
    render(<HostDetailsModal host={hostOf({
      portCount: 1,
      ports: [{ number: 80, protocol: 'tcp', state: 'open', service: 'http' }],
    })} onClose={vi.fn()} />);

    expect(screen.queryByRole('button', { name: /http/i })).toBeNull();
  });

  it('avisa dentro do dialogo quando a consulta de CVEs ficou incompleta', () => {
    // O aviso global do App esconde-se com um painel aberto, ou seja, desaparece
    // exactamente quando o utilizador vem ler os CVEs.
    render(<HostDetailsModal host={hostOf()} onClose={vi.fn()} cveLookupDegraded />);

    expect(screen.getByText(/this list may be incomplete/i)).toBeDefined();
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

  it('o foco volta ao sitio certo mesmo depois de o App voltar a renderizar', () => {
    // O App passa `onClose={() => ...}`, uma funcao nova a cada render. Capturar o
    // "sitio de onde veio" e coisa de uma vez so, a montagem, e nao de cada vez que o
    // pai volta a renderizar -- dai o efeito do foco viver separado do que ouve o
    // teclado. Isto fixa esse comportamento em vez de o deixar depender de quantos
    // renders acontecem no meio.
    const origin = document.createElement('button');
    document.body.appendChild(origin);
    origin.focus();

    const { rerender, unmount } = render(
      <HostDetailsModal host={hostOf()} onClose={() => {}} onTeleport={vi.fn()} />);

    // Tres renders do App, cada um com um onClose novo, como acontece de verdade.
    for (let i = 0; i < 3; i++) {
      rerender(<HostDetailsModal host={hostOf()} onClose={() => {}} onTeleport={vi.fn()} />);
    }

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

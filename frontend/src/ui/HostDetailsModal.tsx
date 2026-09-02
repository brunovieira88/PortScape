import { useEffect, useRef } from 'react';
import type { Host, Port, RiskReason } from '../api/types';
import { bandColor } from '../scene/Building';

/**
 * O que se pode focar com o Tab, por ordem, dentro de um contentor.
 *
 * <p>Le-se o DOM a cada Tab em vez de guardar a lista: metade do conteudo do dialogo e
 * condicional -- o botao de teleporte so existe para hosts que ainda estao na cidade,
 * as portas e as razoes de risco variam com o host -- e uma lista guardada uma vez
 * ficava a apontar para botoes que ja la nao estao.
 */
function focusablesIn(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll<HTMLElement>(
    'a[href], button:not([disabled]), input:not([disabled]), select, textarea, [tabindex]:not([tabindex="-1"])'));
}

export function HostDetailsModal({ host, onClose, onTeleport }: { host: Host, onClose: () => void, onTeleport?: () => void }) {
  const dialog = useRef<HTMLDivElement>(null);

  /**
   * Um dialogo tem de prender o foco enquanto esta aberto.
   *
   * <p>Sem isto o Tab continuava a passear pela pagina por baixo -- que esta tapada
   * mas nao desaparecida -- e o utilizador de teclado ficava a percorrer uma cidade
   * que nao ve para voltar ao que tinha aberto. E ao fechar, o foco caia no principio
   * da pagina em vez de voltar ao dispositivo de onde veio.
   */
  useEffect(() => {
    const returnTo = document.activeElement as HTMLElement | null;
    const focusables = dialog.current ? focusablesIn(dialog.current) : [];
    (focusables[0] ?? dialog.current)?.focus();

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { onClose(); return; }
      if (e.key !== 'Tab' || !dialog.current) { return; }

      const current = focusablesIn(dialog.current);
      if (current.length === 0) { e.preventDefault(); return; }

      const first = current[0];
      const last = current[current.length - 1];
      const active = document.activeElement;

      // So se intervem nas pontas: no meio da lista o Tab do browser ja faz o certo.
      if (!e.shiftKey && active === last) {
        e.preventDefault();
        first.focus();
      } else if (e.shiftKey && active === first) {
        e.preventDefault();
        last.focus();
      } else if (!dialog.current.contains(active)) {
        // O foco estava fora do dialogo (a pagina por baixo, ou o body depois de um
        // clique no fundo escurecido): trazer-lho de volta em vez de o deixar ir.
        e.preventDefault();
        first.focus();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      returnTo?.focus?.();
    };
  }, [onClose]);

  if (!host) return null;

  const ports = host.ports || [];
  const riskReasons = host.riskReasons || [];

  return (
    <div className="absolute inset-0 z-[9999] bg-black/80 backdrop-blur-sm flex items-center justify-center p-8">
      {/* Modal Container. O aria-modal diz aos leitores de ecra para ignorarem o resto
          da pagina enquanto isto esta aberto; o nome vem do IP, que e o titulo que ja
          la estava. */}
      <div
        ref={dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby="host-details-title"
        tabIndex={-1}
        className="bg-[#030d12] border border-[#00f0ff]/30 rounded-xl shadow-[0_0_50px_rgba(0,240,255,0.15)] w-full max-w-4xl max-h-full overflow-hidden flex flex-col relative animate-in fade-in zoom-in-95 duration-200"
      >
        
        {/* Header */}
        <div className="p-6 border-b border-white/10 flex justify-between items-start bg-black/50">
          <div>
            <div className="flex items-center gap-3 mb-2">
              <h2 id="host-details-title" className="text-3xl font-mono text-[#00f0ff] font-bold tracking-wider">{host.ip}</h2>
              {host.change === 'DISAPPEARED' && (
                <span className="bg-gray-800 text-gray-300 text-xs px-2 py-1 rounded tracking-widest uppercase border border-gray-600">Offline Relic</span>
              )}
            </div>
            <div className="text-sm font-mono text-gray-400">
              HOSTNAME: <span className="text-white">{host.hostname?.replace(/\.(home|lan|local)$/i, '') || 'UNKNOWN'}</span>
            </div>
            {/* O fabricante e a unica coisa que diz o QUE a maquina e; o IP so diz
                onde esta. Vem a null quando o nmap nao resolveu o MAC. */}
            {host.vendor && (
              <div className="text-sm font-mono text-gray-400 mt-1">
                VENDOR: <span className="text-white">{host.vendor}</span>
              </div>
            )}
            {host.mac && (
              <div className="text-[11px] font-mono text-gray-600 mt-1 tracking-wider">{host.mac}</div>
            )}
          </div>
          
          <div className="flex items-center gap-2">
            {onTeleport && (
              <button
                onClick={onTeleport}
                className="flex items-center gap-2 text-xs font-mono font-bold uppercase tracking-widest text-[#00f0ff] border border-[#00f0ff]/40 px-3 py-2 rounded hover:bg-[#00f0ff]/10 hover:border-[#00f0ff] transition-colors"
                title="Teleport to this device in the city"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" /><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" /></svg>
                Go To
              </button>
            )}
            <button
              onClick={onClose}
              aria-label="Close host details"
              className="text-gray-500 hover:text-white transition-colors p-2"
            >
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
            </button>
          </div>
        </div>

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto p-6 flex flex-col md:flex-row gap-6 custom-scrollbar">
          
          {/* Left Column (Stats & Risk) */}
          <div className="w-full md:w-1/3 flex flex-col gap-6">
            
            <div className="bg-black/40 border border-white/5 rounded-lg p-5">
              <h3 className="text-xs font-bold text-gray-500 tracking-[0.2em] uppercase mb-4">Risk Profile</h3>
              <div className="flex items-end gap-3 mb-2">
                <span className="text-4xl font-mono font-bold" style={{ color: bandColor(host.riskBand) }}>
                  {host.riskScore ?? 0}
                </span>
                <span className="text-sm text-gray-500 mb-1">/ 100</span>
              </div>
              {/* A mesma cor que pinta o edificio na cidade -- ver o BAND_COLORS. */}
              <div className="text-sm font-bold tracking-widest uppercase"
                   style={{ color: bandColor(host.riskBand) }}>
                BAND: {host.riskBand || 'UNKNOWN'}
              </div>
            </div>

            <div className="bg-black/40 border border-white/5 rounded-lg p-5">
              <h3 className="text-xs font-bold text-gray-500 tracking-[0.2em] uppercase mb-4">System Identity</h3>
              <div className="space-y-4">
                <div>
                  <div className="text-[10px] text-gray-600 mb-1">OS FINGERPRINT</div>
                  <div className="text-sm font-mono text-[#00f0ff]/90 italic">{host.osGuess || 'UNKNOWN OS'}</div>
                </div>
                <div>
                  <div className="text-[10px] text-gray-600 mb-1">FINGERPRINT MATCH</div>
                  <div className="text-sm font-mono text-gray-300">{host.osAccuracy ? `${host.osAccuracy}%` : 'N/A'}</div>
                </div>
                {/* Sem esta nota, um palpite errado a 97% le-se como um facto. O nmap
                    compara a assinatura da pilha TCP com a sua base de dados e devolve
                    o vizinho mais proximo: um dispositivo que nao esteja la sai sempre
                    como outra coisa qualquer, e com confianca alta. O fabricante, esse,
                    vem do prefixo do MAC e e verificavel -- por isso e ele que manda
                    quando os dois discordam. */}
                {host.osGuess && (
                  <div className="text-[10px] text-gray-600 leading-relaxed border-t border-white/5 pt-3">
                    TCP stack signature matched against nmap's database — this is the
                    <span className="text-gray-500"> closest device</span> it knows about,
                    not a reading of the system itself. Where it disagrees with the
                    vendor{host.vendor && <span className="text-gray-400"> ({host.vendor})</span>}, the vendor wins.
                  </div>
                )}
              </div>
            </div>
            
          </div>

          {/* Right Column (Ports & Vulns) */}
          <div className="w-full md:w-2/3 flex flex-col gap-6">
            
            {/* Risk Reasons Log */}
            <div className="bg-black/40 border border-white/5 rounded-lg p-5">
              <h3 className="text-xs font-bold text-gray-500 tracking-[0.2em] uppercase mb-4">Security Audit Log ({riskReasons.length})</h3>
              {riskReasons.length === 0 ? (
                <div className="text-sm text-gray-500 italic">No significant risks detected.</div>
              ) : (
                <div className="space-y-2">
                  {riskReasons.map((r: RiskReason, i: number) => (
                    <div key={i} className="flex items-start gap-3 bg-red-900/10 border border-red-500/20 p-3 rounded">
                      <span className="text-red-500 mt-0.5">⚠</span>
                      <div>
                        <div className="text-xs font-bold text-red-400 mb-1">{r.description}</div>
                        <div className="text-[10px] font-mono text-gray-400">Impact Score: +{r.points}</div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Ports List */}
            <div className="bg-black/40 border border-white/5 rounded-lg p-5">
              <h3 className="text-xs font-bold text-gray-500 tracking-[0.2em] uppercase mb-4">Open Ports ({ports.length})</h3>
              {ports.length === 0 ? (
                <div className="text-sm text-gray-500 italic">No open ports detected.</div>
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {ports.map((p: Port, i: number) => (
                    <div key={i} className="flex items-center gap-3 bg-black/60 border border-white/10 p-2 rounded">
                      <div className="w-12 text-right font-mono text-[#00f0ff] font-bold text-sm">
                        {p.number}
                      </div>
                      <div className="flex-1">
                        <div className="text-xs text-white uppercase tracking-wider">{p.service || 'UNKNOWN'}</div>
                        <div className="text-[10px] font-mono text-gray-500">{p.state} • {p.protocol}</div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

          </div>

        </div>
      </div>
    </div>
  );
}

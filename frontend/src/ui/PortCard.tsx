import { useState } from 'react';
import type { Cve, Port } from '../api/types';
import { severityOf } from '../api/types';
import { explainVector } from '../knowledge/cvss';
import { bandColor } from '../scene/Building';

/**
 * Uma porta aberta e o que se sabe estar mal no que corre nela.
 *
 * Vive à parte do HostDetailsModal porque é a parte que cresce: o modal trata do
 * diálogo (foco, teclado, layout) e isto trata do conteúdo de uma porta.
 *
 * A lista de CVEs vem do backend truncada em `portscape.nvd.max-cves-per-port` e já
 * ordenada do pior CVSS para o menos grave — não se reordena aqui. Um CPE de kernel
 * devolve milhares de CVEs, e é por isso que o `cveTotal` tem de aparecer no ecrã:
 * mostrar 5 sem dizer que eram 22 seria mentir por omissão.
 */
export function PortCard({ port }: { port: Port }) {
  const [open, setOpen] = useState(false);

  const cves = port.cves || [];
  const total = port.cveTotal ?? cves.length;
  const worst = cves.length > 0 ? severityOf(cves[0]) : null;
  const exploited = cves.filter((cve) => cve.kev);

  // O que o nmap identificou a correr aqui. Sem versão não há CVE possível, por isso
  // vale a pena mostrá-la mesmo quando não há falhas conhecidas.
  const running = [port.product, port.version].filter(Boolean).join(' ');

  const header = (
    <>
      <div className="w-12 shrink-0 text-right font-mono text-[#00f0ff] font-bold text-sm">
        {port.number}
      </div>
      <div className="flex-1 min-w-0 text-left">
        <div className="text-xs text-white uppercase tracking-wider truncate">
          {port.service || 'UNKNOWN'}
          {running && <span className="text-gray-400 normal-case tracking-normal"> · {running}</span>}
        </div>
        <div className="text-[11px] font-mono text-gray-400">{port.state} • {port.protocol}</div>
      </div>
      {cves.length > 0 && (
        <div className="flex items-center gap-2 shrink-0">
          {exploited.length > 0 && (
            <span
              title="Confirmed by CISA as exploited in the wild"
              className="text-[10px] font-bold tracking-widest uppercase px-2 py-0.5 rounded border border-[#ff003c]/60 text-[#ff003c] bg-[#ff003c]/15"
            >
              Exploited
            </span>
          )}
          <span
            className="text-[11px] font-mono font-bold px-2 py-0.5 rounded"
            style={{ color: bandColor(worst), backgroundColor: `${bandColor(worst)}26` }}
          >
            {total} CVE{total === 1 ? '' : 'S'}
          </span>
          <svg
            className={`w-3 h-3 text-gray-500 transition-transform ${open ? 'rotate-180' : ''}`}
            fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </div>
      )}
    </>
  );

  const panelId = `port-${port.number}-${port.protocol}-cves`;

  return (
    <div className="bg-black/60 border border-white/10 rounded overflow-hidden">
      {cves.length === 0 ? (
        <div className="flex items-center gap-3 p-2">{header}</div>
      ) : (
        <button
          type="button"
          onClick={() => setOpen(!open)}
          aria-expanded={open}
          aria-controls={panelId}
          className="w-full flex items-center gap-3 p-2 hover:bg-white/5 transition-colors"
        >
          {header}
        </button>
      )}

      {open && cves.length > 0 && (
        <div id={panelId} className="border-t border-white/10 p-4 space-y-4">
          {cves.map((cve) => <CveRow key={cve.id} cve={cve} />)}
          {total > cves.length && (
            <div className="text-[11px] font-mono text-gray-400 pt-1">
              Showing the {cves.length} highest-scoring of {total} known CVEs.
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function CveRow({ cve }: { cve: Cve }) {
  const band = severityOf(cve);
  const chips = explainVector(cve.vector);

  return (
    <div className="border-l-2 pl-3" style={{ borderColor: bandColor(band) }}>
      <div className="flex flex-wrap items-center gap-2 mb-1">
        {/* O link é a saída para a fonte: o painel resume, o NIST é quem manda. */}
        <a
          href={cve.url || `https://nvd.nist.gov/vuln/detail/${cve.id}`}
          target="_blank"
          rel="noreferrer"
          className="font-mono text-sm font-bold text-[#00f0ff] hover:underline"
        >
          {cve.id}
        </a>
        {cve.cvssScore != null && (
          <span
            className="text-[11px] font-mono font-bold px-2 py-0.5 rounded"
            style={{ color: bandColor(band), backgroundColor: `${bandColor(band)}26` }}
          >
            {cve.cvssScore.toFixed(1)} {band}
          </span>
        )}
        {cve.kev && (
          <span className="text-[10px] font-bold tracking-widest uppercase px-2 py-0.5 rounded bg-[#ff003c] text-black">
            Actively exploited{cve.kev.knownRansomwareUse && ' · ransomware'}
          </span>
        )}
      </div>

      {/* O vector traduzido: é ele que explica o número em vez de pedir que se confie
          nele. Os severos ficam a cor, o resto fica cinzento. */}
      {chips.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mb-1.5">
          {chips.map((chip) => (
            <span
              key={chip.code}
              title={chip.code}
              className={`text-[11px] px-2 py-0.5 rounded border ${chip.severe
                ? 'border-[#ff8a00]/50 text-[#ff8a00] bg-[#ff8a00]/15'
                : 'border-white/15 text-gray-300 bg-white/5'}`}
            >
              {chip.label}
            </span>
          ))}
        </div>
      )}

      {cve.description && (
        <p className="text-xs text-gray-300 leading-relaxed line-clamp-3">{cve.description}</p>
      )}

      {/* A CISA não diz só que está a ser explorado -- diz o que fazer a seguir. */}
      {cve.kev?.requiredAction && (
        <p className="text-[11px] text-[#ff8a00] leading-relaxed mt-1.5">
          <span className="uppercase tracking-widest font-bold">CISA: </span>
          {cve.kev.requiredAction}
        </p>
      )}
    </div>
  );
}

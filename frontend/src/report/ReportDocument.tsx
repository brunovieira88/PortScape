import type { Scan } from '../api/types';
import { type HostReport, type PortReport, modelFor } from './model';

/**
 * O relatório como documento imprimível.
 *
 * Está sempre montado e sempre escondido — `display: none` fora de `@media print` (ver
 * `print.css`). Ao chamar `window.print()`, a aplicação desaparece e fica só isto, e o
 * browser produz um PDF com texto pesquisável, sem uma única biblioteca no bundle.
 *
 * Lê o mesmo `model.ts` que o `markdown.ts`: aqui só há sintaxe, o conteúdo e a ordem
 * foram decididos uma vez.
 */
export function ReportDocument({ scan }: { scan: Scan }) {
  const model = modelFor(scan);
  const facts = [
    model.when,
    `${model.deviceCount} ${model.deviceCount === 1 ? 'device' : 'devices'}`,
    model.duration,
  ].filter(Boolean);

  return (
    // aria-hidden: para quem usa leitor de ecra isto e uma copia do que ja esta no
    // painel, e ouvir o relatorio inteiro a seguir a cidade seria ruido.
    <article className="report-document" aria-hidden="true">
      <h1>Portscape — {model.target}</h1>
      <p className="report-facts">{facts.join(' · ')}</p>

      {model.warning && <p className="report-warning">{model.warning}</p>}

      {model.bands.length > 0 && (
        <table>
          <thead>
            <tr><th>Risk</th><th className="num">Devices</th></tr>
          </thead>
          <tbody>
            {model.bands.map((band) => (
              <tr key={band.label}>
                <td>{band.label}</td>
                <td className="num">{band.count}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h2>Inventory</h2>
      {model.inventory.length === 0 ? (
        <p>No devices responded.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Address</th><th>Name</th>
              <th className="num">Score</th><th className="num">Open ports</th>
              <th>Most serious finding</th>
            </tr>
          </thead>
          <tbody>
            {model.inventory.map((row) => (
              <tr key={row.ip}>
                <td><code>{row.ip}</code></td>
                <td>{row.name}</td>
                <td className="num">{row.score}</td>
                <td className="num">{row.portCount}</td>
                <td>{row.worstFinding}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {model.hosts.map((host) => <HostSection key={host.ip} host={host} />)}

      {model.quiet.length > 0 && (
        <section className="report-host">
          <h2>Nothing notable</h2>
          <p>
            {model.quiet.length} {model.quiet.length === 1 ? 'device' : 'devices'} with no
            findings: {model.quiet.map((ip, i) => (
              <span key={ip}>{i > 0 && ', '}<code>{ip}</code></span>
            ))}.
          </p>
        </section>
      )}
    </article>
  );
}

function HostSection({ host }: { host: HostReport }) {
  return (
    <section className="report-host">
      <h2>{host.ip} — {host.name}</h2>
      <p><strong>{host.identity[0]}</strong>{host.identity.slice(1).map((part) => ` · ${part}`)}</p>

      {host.attackPath && (
        <>
          <h3>Likely attack path</h3>
          <p>{[host.attackPath.entry, host.attackPath.impact, host.attackPath.pivot]
            .filter(Boolean).join(' ')}</p>
          <p className="report-tactics">
            {host.attackPath.tactics.map((t) => t.name).join(' → ')}
          </p>
        </>
      )}

      {host.remediation.length > 0 && (
        <>
          <h3>What to fix first</h3>
          <ol>
            {host.remediation.map((step) => (
              <li key={step.action}><strong>{step.action}</strong> — {step.effect}</li>
            ))}
          </ol>
        </>
      )}

      {host.ports.length > 0 && (
        <>
          <h3>Open ports</h3>
          {host.ports.map((port) => <PortBlock key={port.title} port={port} />)}
        </>
      )}
    </section>
  );
}

function PortBlock({ port }: { port: PortReport }) {
  return (
    <div className="report-port">
      <h4>{port.title}</h4>

      {port.dossier && (
        <>
          <p>{port.dossier.name}. {port.dossier.body}</p>
          <ul>
            {port.dossier.hardening.map((step) => <li key={step}>{step}</li>)}
          </ul>
        </>
      )}

      {port.cves.length > 0 && (
        <ul>
          {port.cves.map((cve) => (
            <li key={cve.id}>
              <a href={cve.url}>{cve.id}</a>
              {cve.qualifier && ` — ${cve.qualifier}`}
              {cve.description && <><br />{cve.description}</>}
            </li>
          ))}
        </ul>
      )}

      {port.truncated && <p>{port.truncated}</p>}
    </div>
  );
}

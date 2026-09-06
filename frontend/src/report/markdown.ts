import type { Scan } from '../api/types';
import {
  type HostReport, type PortReport, type ReportModel, modelFor, reportSlug,
} from './model';

/**
 * O modelo do relatório em Markdown.
 *
 * Só sintaxe: o que entra, por que ordem e com que palavras já foi decidido em
 * `model.ts`, que é a mesma fonte que o `Document.tsx` usa para imprimir. Duas funções a
 * decidir o mesmo conteúdo é como as coisas divergem.
 *
 * **Sem marca de água, sem rodapé, sem "gerado por", sem emoji.** Um documento que se
 * possa colar num ticket sem ter de se apagar ruído primeiro — se o leitor precisar de
 * saber de onde veio, o título diz-lhe.
 */
export function reportFor(scan: Scan): string {
  return renderMarkdown(modelFor(scan));
}

export function reportFilename(scan: Scan): string {
  return `${reportSlug(scan)}.md`;
}

export function renderMarkdown(model: ReportModel): string {
  const blocks: string[] = [heading(model)];

  if (model.bands.length > 0) {
    blocks.push([
      '| Risk | Devices |', '|---|---|',
      ...model.bands.map((band) => `| ${band.label} | ${band.count} |`),
    ].join('\n'));
  }

  blocks.push(inventory(model));
  blocks.push(...model.hosts.map(hostSection));

  if (model.quiet.length > 0) {
    const listed = model.quiet.map((ip) => `\`${ip}\``).join(', ');
    blocks.push(['## Nothing notable', '',
      `${model.quiet.length} ${model.quiet.length === 1 ? 'device' : 'devices'} `
      + `with no findings: ${listed}.`].join('\n'));
  }

  return blocks.filter(Boolean).join('\n\n') + '\n';
}

function heading(model: ReportModel): string {
  const lines = [`# Portscape — ${model.target}`, ''];
  const facts = [
    model.when,
    `${model.deviceCount} ${model.deviceCount === 1 ? 'device' : 'devices'}`,
    model.duration,
  ].filter(Boolean);
  lines.push(facts.join(' · '));

  if (model.warning) {
    lines.push('', `> **${model.warning.split('.')[0]}.**${model.warning.slice(
      model.warning.indexOf('.') + 1)}`);
  }
  return lines.join('\n');
}

function inventory(model: ReportModel): string {
  if (model.inventory.length === 0) {
    return '## Inventory\n\nNo devices responded.';
  }
  return [
    '## Inventory', '',
    '| Address | Name | Score | Open ports | Most serious finding |',
    '|---|---|---:|---:|---|',
    ...model.inventory.map((row) =>
      `| \`${row.ip}\` | ${cell(row.name)} | ${row.score} | ${row.portCount} `
      + `| ${cell(row.worstFinding)} |`),
  ].join('\n');
}

function hostSection(host: HostReport): string {
  const lines = [`## ${host.ip} — ${host.name}`, ''];
  lines.push(`**${host.identity[0]}**${host.identity.slice(1).map((p) => ` · ${p}`).join('')}`);

  if (host.attackPath) {
    const path = host.attackPath;
    lines.push('', '### Likely attack path', '');
    lines.push([path.entry, path.impact, path.pivot].filter(Boolean).join(' '));
    lines.push('', `*${path.tactics.map((t) => t.name).join(' → ')}*`);
  }

  if (host.remediation.length > 0) {
    lines.push('', '### What to fix first', '');
    host.remediation.forEach((step, i) => {
      lines.push(`${i + 1}. **${step.action}** — ${step.effect}`);
    });
  }

  if (host.ports.length > 0) {
    lines.push('', '### Open ports', '');
    lines.push(host.ports.map(portBlock).join('\n\n'));
  }
  return lines.join('\n');
}

function portBlock(port: PortReport): string {
  const lines = [`**${port.title}**`];

  if (port.dossier) {
    lines.push('', `${port.dossier.name}. ${port.dossier.body}`);
    lines.push('', ...port.dossier.hardening.map((step) => `- ${step}`));
  }
  if (port.cves.length > 0) {
    lines.push('', ...port.cves.map((cve) => {
      const qualifier = cve.qualifier ? ` — ${cve.qualifier}` : '';
      // Duas espacos no fim: quebra de linha em Markdown, sem abrir paragrafo novo.
      const description = cve.description ? `  \n  ${cve.description}` : '';
      return `- [${cve.id}](${cve.url})${qualifier}${description}`;
    }));
  }
  if (port.truncated) {
    lines.push('', port.truncated);
  }
  return lines.join('\n');
}

/** Uma barra vertical numa célula parte a tabela toda. */
function cell(text: string): string {
  return text.replace(/\|/g, '\\|').replace(/\n+/g, ' ');
}

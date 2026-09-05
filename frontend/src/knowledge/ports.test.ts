import { describe, expect, it } from 'vitest';
// Importado como texto pelo Vite (`?raw`), e nao lido com `node:fs`: assim o
// tsconfig.app.json continua sem os tipos de Node -- que o codigo da app nao pode ter,
// porque nao corre em Node.
import applicationYml from '../../../backend/src/main/resources/application.yml?raw';
import { documentedPorts, dossierFor } from './ports';

/**
 * O dossiê vive no frontend, os pesos vivem no `application.yml` do backend. Sem uma
 * amarra entre os dois, uma porta ganha peso e fica sem explicação, e ninguém dá por
 * isso — foi exactamente assim que cinco códigos de risco obsoletos sobreviveram meses
 * nas fixtures da demo.
 *
 * O caminho do import é frágil de propósito: se o `application.yml` mudar de sítio, o
 * build parte. Um teste que lê um ficheiro que já não existe e conclui "está tudo bem"
 * é pior do que não haver teste nenhum.
 */
const APPLICATION_YML = 'backend/src/main/resources/application.yml';

/**
 * As portas de `portscape.risk.port-weights`, com o peso que o backend lhes dá.
 *
 * Lido linha a linha e não com um regex sobre o ficheiro inteiro: o `application.yml`
 * está gravado com CRLF, e um padrão que procure uma quebra `\n` a seguir à chave não
 * encontra nada. O `throw` existe para esse caso não passar por "não há portas pesadas"
 * — um teste de cobertura que lê um mapa vazio e conclui que está tudo bem é pior do
 * que não haver teste nenhum.
 */
function portWeights(): Map<number, number> {
  const lines = applicationYml.split(/\r?\n/);
  const start = lines.findIndex((line) => /^\s*port-weights:\s*$/.test(line));
  if (start === -1) {
    throw new Error(`Nao encontrei a chave port-weights em ${APPLICATION_YML}`);
  }

  const weights = new Map<number, number>();
  for (const line of lines.slice(start + 1)) {
    if (/^\s*(#.*)?$/.test(line)) continue;   // comentário solto ou linha vazia
    const row = /^\s+(\d+):\s*(\d+)/.exec(line);
    if (!row) break;                          // primeira linha que não é uma porta: fim do bloco
    weights.set(Number(row[1]), Number(row[2]));
  }
  return weights;
}

/**
 * O corte. Abaixo disto o peso é baixo o suficiente para a porta não precisar de
 * justificação escrita; a partir daqui, o painel diz "+25" ou mais e tem de explicar
 * porquê. Subir este número é afrouxar a regra — se for preciso, que seja deliberado.
 */
const EXPLANATION_REQUIRED_AT = 25;

describe('port dossier', () => {
  it('lê mesmo os pesos do backend -- se este ficheiro falhar, o resto nao vale nada', () => {
    const weights = portWeights();

    expect(weights.size).toBeGreaterThan(10);
    // Ancoras: se estes dois mudarem, é uma decisão editorial e não um acidente.
    expect(weights.get(23)).toBe(35);
    expect(weights.get(443)).toBe(2);
  });

  it('toda a porta que o backend penaliza a serio tem explicacao', () => {
    const heavy = [...portWeights().entries()]
      .filter(([, weight]) => weight >= EXPLANATION_REQUIRED_AT)
      .map(([port]) => port);

    const undocumented = heavy.filter((port) => !dossierFor(port));

    expect(undocumented,
      `Estas portas valem ${EXPLANATION_REQUIRED_AT}+ pontos e nao explicam porque: `
      + `${undocumented.join(', ')}. Acrescenta-as em knowledge/ports.ts.`)
      .toEqual([]);
  });

  it('o dossie nao cresce para portas que a ferramenta nunca pontua', () => {
    const weights = portWeights();

    const orphans = documentedPorts().filter((port) => !weights.has(port));

    // Uma entrada para uma porta sem peso próprio nunca chega ao ecrã com um número ao
    // lado, e passa a ser texto que ninguém revê.
    expect(orphans,
      `Estas portas tem dossie mas nao tem peso no application.yml: ${orphans.join(', ')}.`)
      .toEqual([]);
  });

  it('cada entrada esta completa -- meio dossie e pior do que nenhum', () => {
    for (const port of documentedPorts()) {
      const dossier = dossierFor(port)!;

      expect(dossier.name, `porta ${port}`).toBeTruthy();
      expect(dossier.summary.length, `porta ${port}: summary`).toBeGreaterThan(40);
      expect(dossier.whyItExists.length, `porta ${port}: whyItExists`).toBeGreaterThan(40);
      expect(dossier.attackerValue.length, `porta ${port}: attackerValue`).toBeGreaterThan(40);
      expect(dossier.hardening.length, `porta ${port}: hardening`).toBeGreaterThan(0);
    }
  });

  it('inclui portas que estao bem, para nao ser um alarme que toca sempre', () => {
    // Sem uma porta que o dossiê diga que é normal, tudo o que ele diz soa a acusação.
    for (const port of [22, 80, 443]) {
      expect(dossierFor(port), `porta ${port}`).toBeDefined();
    }
  });

  it('uma porta sem entrada devolve undefined, nao um dossie vazio', () => {
    expect(dossierFor(9999)).toBeUndefined();
  });
});

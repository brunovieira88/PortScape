/**
 * Traduz um vector CVSS para linguagem corrente.
 *
 * O vector é a anatomia da falha, e é a diferença entre mostrar um 8.1 e explicá-lo:
 * `AV:N/AC:L/PR:N/UI:N` quer dizer *alcançável pela rede, sem credenciais, sem a vítima
 * fazer nada* — que é o que decide se aquilo é urgente ou académico.
 *
 * Vive no frontend e não no backend porque é apresentação, e porque tem de funcionar no
 * modo demo, onde não há backend nenhum para traduzir seja o que for.
 *
 * Três formatos, e todos aparecem em dados reais — um scan a um serviço com histórico
 * devolve os três de uma vez:
 *
 *   - v2:   sem prefixo, autenticação em `Au` — `AV:N/AC:M/Au:N/C:C/I:C/A:C` (26 chars)
 *   - v3.x: prefixo `CVSS:3.1/`, impacto em `C`/`I`/`A` (44 chars)
 *   - v4.0: prefixo `CVSS:4.0/`, impacto em `VC`/`VI`/`VA`, e mais 20 métricas
 *           opcionais que o NVD escreve todas como `:X` (174 chars)
 *
 * Daí a tabela ser indexada por código e o `X` ser ignorado em bloco: sem isso, um
 * vector v4.0 dava 32 chips e 26 deles diziam "não definido".
 */

/** Um pedaço legível do vector. `severe` é o que o painel destaca. */
export interface CvssChip {
  /** Código original (`AV:N`), para chave de render e para depuração. */
  code: string;
  label: string;
  severe: boolean;
}

interface Metric {
  /** Ordem de leitura: como é alcançada, o que é preciso, e o que custa. */
  order: number;
  values: Record<string, { label: string; severe?: boolean }>;
}

/**
 * As métricas que respondem a "isto é urgente?".
 *
 * Deliberadamente parcial. Ficam de fora o `S`/`SC`/`SI`/`SA` (scope), o `E` (maturidade
 * do exploit) e todo o grupo ambiental: ou exigem contexto que o Portscape não tem, ou
 * dizem pouco a quem está a olhar para a rede de casa. Um código desconhecido é ignorado
 * em silêncio, para um formato futuro degradar em vez de rebentar.
 */
const METRICS: Record<string, Metric> = {
  // Attack Vector — v2, v3.x e v4.0 usam o mesmo código.
  AV: {
    order: 1,
    values: {
      N: { label: 'reachable from the network', severe: true },
      A: { label: 'same network segment only' },
      L: { label: 'needs local access' },
      P: { label: 'needs physical access' },
    },
  },
  // Attack Complexity.
  AC: {
    order: 2,
    values: {
      L: { label: 'works reliably', severe: true },
      M: { label: 'some conditions apply' },
      H: { label: 'needs special conditions' },
    },
  },
  // Privileges Required (v3.x, v4.0).
  PR: {
    order: 3,
    values: {
      N: { label: 'no account needed', severe: true },
      L: { label: 'needs an ordinary account' },
      H: { label: 'needs an admin account' },
    },
  },
  // Authentication (v2) — o antepassado do PR, com outros valores.
  Au: {
    order: 3,
    values: {
      N: { label: 'no account needed', severe: true },
      S: { label: 'needs an account' },
      M: { label: 'needs several accounts' },
    },
  },
  // User Interaction.
  UI: {
    order: 4,
    values: {
      N: { label: 'no user action needed', severe: true },
      P: { label: 'needs the user to act' },
      R: { label: 'needs the user to act' },
    },
  },
  // Impacto: confidencialidade, integridade, disponibilidade. O v4.0 prefixa com V, e o
  // v2 usa C (complete) e P (partial) onde o v3 usa H e L.
  C: {
    order: 5,
    values: {
      H: { label: 'reads everything', severe: true },
      C: { label: 'reads everything', severe: true },
      P: { label: 'reads some data' },
      L: { label: 'reads some data' },
      N: { label: '' },
    },
  },
  VC: {
    order: 5,
    values: {
      H: { label: 'reads everything', severe: true },
      L: { label: 'reads some data' },
      N: { label: '' },
    },
  },
  I: {
    order: 6,
    values: {
      H: { label: 'alters everything', severe: true },
      C: { label: 'alters everything', severe: true },
      P: { label: 'alters some data' },
      L: { label: 'alters some data' },
      N: { label: '' },
    },
  },
  VI: {
    order: 6,
    values: {
      H: { label: 'alters everything', severe: true },
      L: { label: 'alters some data' },
      N: { label: '' },
    },
  },
  A: {
    order: 7,
    values: {
      H: { label: 'can take it down', severe: true },
      C: { label: 'can take it down', severe: true },
      P: { label: 'degrades the service' },
      L: { label: 'degrades the service' },
      N: { label: '' },
    },
  },
  VA: {
    order: 7,
    values: {
      H: { label: 'can take it down', severe: true },
      L: { label: 'degrades the service' },
      N: { label: '' },
    },
  },
};

/**
 * @param vector o vector tal como o NVD o publica, com ou sem o prefixo `CVSS:n.n/`
 * @returns os pedaços legíveis, por ordem de leitura; vazio se não houver nada a dizer
 */
export function explainVector(vector: string | null | undefined): CvssChip[] {
  if (!vector) return [];

  const chips: { chip: CvssChip; order: number }[] = [];
  for (const part of vector.split('/')) {
    const [code, value] = part.split(':');
    // O prefixo "CVSS:4.0" cai aqui: "CVSS" não está na tabela.
    if (!code || !value) continue;
    // "X" é "não definida" — num vector v4.0 do NVD são 20 das 32 métricas.
    if (value === 'X') continue;

    const metric = METRICS[code];
    const meaning = metric?.values[value];
    // Um impacto nulo (C:N) tem label vazia: é ruído, não informação.
    if (!meaning || !meaning.label) continue;

    chips.push({
      chip: { code: part, label: meaning.label, severe: meaning.severe === true },
      order: metric.order,
    });
  }

  return chips.sort((a, b) => a.order - b.order).map(({ chip }) => chip);
}

/** A versão do CVSS que o vector declara. O v2 não se identifica, daí o fallback. */
export function cvssVersionOf(vector: string | null | undefined): string | null {
  if (!vector) return null;
  const match = /^CVSS:([\d.]+)\//.exec(vector);
  if (match) return match[1];
  // Só o v2 tem métrica de autenticação; sem prefixo e sem Au, não se arrisca.
  return vector.includes('Au:') ? '2.0' : null;
}

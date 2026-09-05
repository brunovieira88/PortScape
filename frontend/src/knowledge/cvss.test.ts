import { describe, expect, it } from 'vitest';
import { cvssVersionOf, explainVector } from './cvss';

/**
 * Os vectores destes testes são reais: saíram de um scan contra o NVD a `OpenSSH 9.6`
 * e a `Apache Log4j 2.14.1`, que entre eles devolvem os três formatos de uma vez.
 */
const V2 = 'AV:N/AC:M/Au:N/C:C/I:C/A:C';
const V31 = 'CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:H/I:H/A:H';
const V40 = 'CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:N/VI:N/VA:N/SC:N/SI:L/SA:N/E:X/CR:X/IR:X'
  + '/AR:X/MAV:X/MAC:X/MAT:X/MPR:X/MUI:X/MVC:X/MVI:X/MVA:X/MSC:X/MSI:X/MSA:X/S:X/AU:X'
  + '/R:X/V:X/RE:X/U:X';

const labels = (vector: string) => explainVector(vector).map((chip) => chip.label);

describe('explainVector', () => {
  it('explica um vector v3.1 pela ordem em que se le um ataque', () => {
    // Como chega, o que precisa, e o que custa -- e nao a ordem em que o NVD escreve.
    expect(labels(V31)).toEqual([
      'reachable from the network',
      'needs special conditions',
      'no account needed',
      'no user action needed',
      'reads everything',
      'alters everything',
      'can take it down',
    ]);
  });

  it('le o v2, que nao tem prefixo e chama Au ao que o v3 chama PR', () => {
    expect(labels(V2)).toEqual([
      'reachable from the network',
      'some conditions apply',
      'no account needed',
      'reads everything',
      'alters everything',
      'can take it down',
    ]);
  });

  it('num vector v4.0 real ignora as 20 metricas que o NVD escreve como nao definidas', () => {
    // Sem isso, este vector dava 32 chips e 26 diziam "nao definido". O impacto aqui e
    // todo N (nenhum), por isso so sobra a parte de como a falha e alcancada.
    expect(labels(V40)).toEqual([
      'reachable from the network',
      'works reliably',
      'no account needed',
      'no user action needed',
    ]);
  });

  it('marca como severo o que torna a falha urgente', () => {
    const severe = explainVector(V31).filter((chip) => chip.severe).map((chip) => chip.code);

    // Alcancavel pela rede, sem conta e sem accao da vitima -- e por aqui que se
    // distingue uma falha explorada em massa de uma que exige o atacante ja la dentro.
    expect(severe).toEqual(['AV:N', 'PR:N', 'UI:N', 'C:H', 'I:H', 'A:H']);
  });

  it('um impacto nulo nao vira chip -- e ruido, nao informacao', () => {
    expect(labels('CVSS:3.1/AV:L/C:N/I:N/A:H')).toEqual([
      'needs local access',
      'can take it down',
    ]);
  });

  it('um codigo desconhecido e ignorado, para um formato futuro degradar em vez de rebentar', () => {
    expect(labels('CVSS:5.0/AV:N/ZZ:Q/QQ:9')).toEqual(['reachable from the network']);
  });

  it('sem vector nao ha nada a dizer', () => {
    expect(explainVector('')).toEqual([]);
    expect(explainVector(null)).toEqual([]);
    expect(explainVector(undefined)).toEqual([]);
  });
});

describe('cvssVersionOf', () => {
  it('le a versao do prefixo quando ela la esta', () => {
    expect(cvssVersionOf(V31)).toBe('3.1');
    expect(cvssVersionOf(V40)).toBe('4.0');
  });

  it('reconhece o v2 pelo Au, que e a unica versao que o tem', () => {
    expect(cvssVersionOf(V2)).toBe('2.0');
  });

  it('nao inventa uma versao quando nao ha por onde a saber', () => {
    expect(cvssVersionOf('AV:N/AC:L')).toBeNull();
    expect(cvssVersionOf(null)).toBeNull();
  });
});

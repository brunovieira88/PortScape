/**
 * As tácticas do MITRE ATT&CK que este projecto usa, e mais nenhuma.
 *
 * Uma tabela fechada e curta de propósito. O ATT&CK tem catorze tácticas e centenas de
 * técnicas; o Portscape vê portas abertas e versões de software, e só consegue falar com
 * honestidade das cinco que se deduzem daí. Nomear uma técnica concreta — *"T1110.001,
 * password guessing"* — seria afirmar como é que o ataque aconteceria, e isso é
 * precisamente o que não se faz aqui.
 *
 * Serve para dar vocabulário reconhecível a quem lê, não para classificar o incidente.
 */

export type MitreTacticId = 'TA0001' | 'TA0002' | 'TA0006' | 'TA0008' | 'TA0010';

export interface MitreTactic {
  id: MitreTacticId;
  name: string;
  url: string;
}

const NAMES: Record<MitreTacticId, string> = {
  TA0001: 'Initial Access',
  TA0002: 'Execution',
  TA0006: 'Credential Access',
  TA0008: 'Lateral Movement',
  TA0010: 'Exfiltration',
};

/**
 * A ordem em que uma cadeia se lê, e não a ordem numérica: primeiro entra-se, depois
 * corre-se alguma coisa, depois colhem-se credenciais, depois anda-se de lado.
 */
const CHAIN_ORDER: MitreTacticId[] = ['TA0001', 'TA0002', 'TA0006', 'TA0008', 'TA0010'];

export function tacticsOf(ids: readonly MitreTacticId[]): MitreTactic[] {
  const unique = [...new Set(ids)];
  unique.sort((a, b) => CHAIN_ORDER.indexOf(a) - CHAIN_ORDER.indexOf(b));
  return unique.map((id) => ({
    id,
    name: NAMES[id],
    url: `https://attack.mitre.org/tactics/${id}/`,
  }));
}

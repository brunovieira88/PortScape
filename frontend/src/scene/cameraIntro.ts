/**
 * O voo de chegada a cidade.
 *
 * <p>A camara nasce no ar, olha para baixo, e desce ate a altura dos olhos enquanto
 * endireita o olhar. E a primeira coisa que se ve de um scan, e serve para mostrar a
 * planta toda antes de deixar o utilizador ao nivel da rua, onde ja so ve a sua rua.
 *
 * <p>Logica pura de propositio: as coordenadas por onde o voo passa -- e sobretudo onde
 * ele <i>acaba</i> -- sao a parte que interessa fixar em teste. Ver o spawnPointFor no
 * cityGrid, que e quem escolhe o sitio.
 */

/** Duracao do voo, em segundos. */
export const INTRO_DURATION = 3.0;
/** Altura a que a camara nasce. */
export const INTRO_START_Y = 150;
/** Recuo em z face ao ponto de chegada, para o voo avancar enquanto desce. */
export const INTRO_START_Z_OFFSET = 40;
/** Inclinacao inicial: a olhar para baixo a 45 graus. */
export const INTRO_START_PITCH = -Math.PI / 4;

export interface IntroFrame {
  /** Altura da camara neste instante. */
  y: number;
  /** Recuo em z que ainda falta percorrer ate ao ponto de chegada. */
  zOffset: number;
  /** Inclinacao vertical do olhar. */
  pitch: number;
  done: boolean;
}

/**
 * O estado do voo a {@code elapsed} segundos do inicio.
 *
 * <p>O amortecimento e um {@code ease-out} cubico: comeca depressa e chega devagar, que
 * e o que faz a descida parecer uma aterragem e nao uma queda. Passado o tempo todo os
 * valores ficam exatamente nos de chegada -- nao "quase la" -- para a camara nao ficar
 * a pairar meio metro acima do chao com uma inclinacao residual.
 */
export function introFrame(elapsed: number, groundY: number): IntroFrame {
  const progress = Math.min(Math.max(elapsed, 0) / INTRO_DURATION, 1);
  const eased = 1 - Math.pow(1 - progress, 3);

  return {
    y: INTRO_START_Y * (1 - eased) + groundY * eased,
    zOffset: INTRO_START_Z_OFFSET * (1 - eased),
    // `|| 0` porque um pitch negativo vezes zero da -0, que e o mesmo angulo mas nao
    // o mesmo valor para quem compare.
    pitch: INTRO_START_PITCH * (1 - eased) || 0,
    done: progress >= 1,
  };
}

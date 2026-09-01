import { describe, it, expect } from 'vitest';
import { introFrame, INTRO_DURATION, INTRO_START_Y, INTRO_START_PITCH,
         INTRO_START_Z_OFFSET } from './cameraIntro';

const STREET_Y = 1.7;

describe('introFrame', () => {

  it('comeca no ar, a olhar para baixo e recuado', () => {
    const frame = introFrame(0, STREET_Y);

    expect(frame.y).toBe(INTRO_START_Y);
    expect(frame.pitch).toBe(INTRO_START_PITCH);
    expect(frame.zOffset).toBe(INTRO_START_Z_OFFSET);
    expect(frame.done).toBe(false);
  });

  it('acaba exatamente a altura dos olhos, no ponto de chegada e a olhar em frente', () => {
    // "Quase la" nao chega: um residuo deixava a camara a pairar acima do chao com o
    // olhar torto, e dali em diante e a posicao dela que manda em tudo o resto.
    const frame = introFrame(INTRO_DURATION, STREET_Y);

    expect(frame.y).toBe(STREET_Y);
    expect(frame.zOffset).toBe(0);
    expect(frame.pitch).toBe(0);
    expect(frame.done).toBe(true);
  });

  it('nao passa do fim por muito que se ande para a frente no tempo', () => {
    const frame = introFrame(INTRO_DURATION * 10, STREET_Y);

    expect(frame.y).toBe(STREET_Y);
    expect(frame.done).toBe(true);
  });

  it('desce sempre, sem voltar para tras', () => {
    let anterior = Infinity;
    for (let t = 0; t <= INTRO_DURATION; t += 0.05) {
      const { y } = introFrame(t, STREET_Y);
      expect(y).toBeLessThanOrEqual(anterior);
      anterior = y;
    }
  });

  it('trava a chegada: percorre mais de metade da descida no primeiro terco', () => {
    // E o ease-out cubico que faz isto parecer uma aterragem e nao uma queda.
    const percorrido = (t: number) =>
      (INTRO_START_Y - introFrame(t, STREET_Y).y) / (INTRO_START_Y - STREET_Y);

    expect(percorrido(INTRO_DURATION / 3)).toBeGreaterThan(0.5);
    expect(percorrido(INTRO_DURATION * 2 / 3)).toBeGreaterThan(0.9);
  });

  it('um tempo negativo nao poe a camara acima do ponto de partida', () => {
    expect(introFrame(-5, STREET_Y).y).toBe(INTRO_START_Y);
  });
});

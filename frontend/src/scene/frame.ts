/**
 * O maior passo de tempo que uma animacao aceita, em segundos.
 *
 * <p>O R3F passa o {@code delta} do relogio em bruto -- {@code state.clock.getDelta()},
 * sem limite nenhum. Com o separador em segundo plano o {@code requestAnimationFrame}
 * para mas o relogio nao, portanto o primeiro frame ao voltar traz todo o tempo que
 * esteve escondido: um minuto fora da aba dava um delta de 60.
 *
 * <p>Isso rebenta com qualquer {@code lerp(a, b, k * delta)}. Com k=10, o alfa ia a
 * 600 -- muito acima de 1, o que deixa de interpolar e passa a extrapolar: a rotacao
 * da camara saltava para um valor arbitrario e a escala dos edificios disparava para
 * ~238x, demorando depois dois segundos a encolher de volta a olhos vistos.
 *
 * <p>50 ms e um frame a 20 fps. Abaixo disso nada muda; acima, a animacao continua de
 * onde estava em vez de dar um salto -- que e exatamente o que se quer ao voltar a uma
 * aba: retomar, nao teletransportar.
 */
export const MAX_FRAME_DELTA = 0.05;

export function stepDelta(delta: number): number {
  return Math.min(delta, MAX_FRAME_DELTA);
}

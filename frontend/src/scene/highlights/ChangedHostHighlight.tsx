import { HostStateMarker } from './HostStateMarker';

/**
 * Um dispositivo que ja estava no inventario mas mudou: o mesmo fio, interrompido.
 *
 * <p>Ja foi um anel de varrimento a subir e descer dentro de uma gaiola de wireframe
 * laranja. O anel tinha quatro segmentos, o que o fazia passar por dentro de qualquer
 * edificio mais largo do que 6.4 -- via-se so aos bocados, como se estivesse partido.
 */
export function ChangedHostHighlight({ radius }: { radius?: number }) {
  return <HostStateMarker radius={radius} dashed />;
}

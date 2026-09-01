import { HostStateMarker } from './HostStateMarker';

/**
 * Um dispositivo que nao estava no inventario: fio inteiro, a respirar devagar.
 *
 * <p>Ja foi uma coluna de laser amarela de 60 unidades com aneis a rodar. Chamava mais
 * atencao do que a cidade toda, atravessava os edificios altos e, pior, punha uma
 * segunda cor a disputar significado com a cor do risco.
 */
export function NewHostHighlight({ radius }: { radius?: number }) {
  return <HostStateMarker radius={radius} pulse />;
}

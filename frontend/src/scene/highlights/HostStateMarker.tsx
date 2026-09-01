import { useRef } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';

/** Folga entre a fachada e a marca. */
const CLEARANCE = 0.6;
/** Espessura do fio. Fino de proposito: e uma anotacao, nao um efeito. */
const STROKE = 0.35;
/** Rente ao chao, mas acima do brilho do edificio (que esta a 0.06). */
const GROUND_Y = 0.09;

/**
 * A marca de estado de um host: um fio de luz no chao, a volta da base do edificio.
 *
 * <p>Substitui os holofotes que aqui estavam -- uma coluna de laser amarela de 60
 * unidades e uma gaiola de wireframe -- que competiam com os proprios edificios,
 * atravessavam a geometria deles e roubavam a cena a cor do risco, que e a unica cor
 * que aqui carrega informacao.
 *
 * <p>Uma so linguagem para os dois estados, e branca: o <b>novo</b> e um fio inteiro a
 * respirar devagar, o <b>alterado</b> e o mesmo fio interrompido. A distincao e no
 * padrao e nao na cor, para nao haver duas paletas a disputar a mesma cidade.
 */
export function HostStateMarker({ radius = 9, dashed = false, pulse = false }:
  { radius?: number, dashed?: boolean, pulse?: boolean }) {
  const ref = useRef<THREE.Group>(null);

  const inner = radius + CLEARANCE;
  // Quatro arcos com quatro cortes -- o suficiente para se ler como interrompido sem
  // parecer que faltam pedacos por engano.
  const arcs = dashed ? [0, 0.5, 1, 1.5].map(turn => turn * Math.PI / 2) : [0];
  const sweep = dashed ? Math.PI / 2 * 0.62 : Math.PI * 2;

  useFrame((state) => {
    if (!ref.current) { return; }
    const breath = pulse ? 0.42 + Math.sin(state.clock.elapsedTime * 1.4) * 0.16 : 0.34;
    for (const arc of ref.current.children) {
      ((arc as THREE.Mesh).material as THREE.MeshBasicMaterial).opacity = breath;
    }
  });

  return (
    <group ref={ref} position={[0, GROUND_Y, 0]} rotation={[-Math.PI / 2, 0, 0]}>
      {arcs.map((start, i) => (
        <mesh key={i}>
          <ringGeometry args={[inner, inner + STROKE, 64, 1, start, sweep]} />
          <meshBasicMaterial color="#ffffff" transparent opacity={0.4}
                             side={THREE.DoubleSide} depthWrite={false} toneMapped={false} />
        </mesh>
      ))}
    </group>
  );
}

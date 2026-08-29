import { useMemo } from 'react';
import * as THREE from 'three';

// Props para a cidade (decorativos)
export function StreetDetails({ layoutWidth, layoutDepth, scale }: { layoutWidth: number, layoutDepth: number, scale: number }) {
  
  // Vamos gerar os detalhes de forma determinística mas aleatória para preencher a cidade
  const props = useMemo(() => {
    const items = [];
    const offsetX = -layoutWidth / 2;
    const offsetZ = -layoutDepth / 2;

    for (let x = 0; x < layoutWidth; x++) {
      for (let z = 0; z < layoutDepth; z++) {
        const realX = (x + offsetX) * scale;
        const realZ = (z + offsetZ) * scale;

        // O centro da rua fica a + (scale / 2) de cada edifício
        const streetX = realX + scale / 2;
        const streetZ = realZ + scale / 2;

        // 1. Candeeiros de Rua (Streetlights)
        // Colocamos candeeiros em algumas interseções com brilho néon
        if (Math.random() > 0.6) {
          items.push(
            <group key={`light-${x}-${z}`} position={[streetX - 1, 0, realZ]}>
              {/* Poste */}
              <mesh position={[0, 2, 0]}>
                <cylinderGeometry args={[0.05, 0.05, 4]} />
                <meshBasicMaterial color="#333333" />
              </mesh>
              {/* Topo do candeeiro */}
              <mesh position={[0.5, 4, 0]} rotation={[0, 0, Math.PI / 2]}>
                <cylinderGeometry args={[0.05, 0.05, 1]} />
                <meshBasicMaterial color="#333333" />
              </mesh>
              {/* Luz Néon (Lâmpada) */}
              <mesh position={[0.8, 3.9, 0]}>
                <boxGeometry args={[0.2, 0.1, 0.1]} />
                {/* Cor intensa para o Bloom apanhar */}
                <meshBasicMaterial color="#ffffff" toneMapped={false} />
              </mesh>
            </group>
          );
        }

        // 2. Cyber-Plantas / Árvores Geométricas (Neon Trees)
        // Colocadas nos passeios
        if (Math.random() > 0.7) {
          items.push(
            <group key={`tree-${x}-${z}`} position={[realX, 0, streetZ - 1]}>
              {/* Tronco */}
              <mesh position={[0, 1, 0]}>
                <cylinderGeometry args={[0.1, 0.1, 2]} />
                <meshBasicMaterial color="#00ffaa" wireframe={true} transparent opacity={0.3} toneMapped={false} />
              </mesh>
              {/* Folhas (Icosaedro wireframe) */}
              <mesh position={[0, 2.5, 0]}>
                <icosahedronGeometry args={[1, 1]} />
                <meshBasicMaterial color="#00ffaa" wireframe={true} transparent opacity={0.5} toneMapped={false} />
              </mesh>
            </group>
          );
        }

        // 3. Hologramas / Sinais Flutuantes
        if (Math.random() > 0.85) {
          const signColors = ['#ff003c', '#fcee0a', '#00f0ff'];
          const color = signColors[Math.floor(Math.random() * signColors.length)];
          
          items.push(
            <mesh key={`sign-${x}-${z}`} position={[realX + 2, 4 + Math.random() * 4, realZ + 2]} rotation={[0, Math.random() * Math.PI, 0]}>
              <planeGeometry args={[2, 1, 4, 2]} />
              <meshBasicMaterial color={color} wireframe={true} transparent opacity={0.7} toneMapped={false} side={THREE.DoubleSide} />
            </mesh>
          );
        }
      }
    }
    return items;
  }, [layoutWidth, layoutDepth, scale]);

  return <>{props}</>;
}

import { useMemo } from 'react';
import * as THREE from 'three';

const SCALE = 13;
const BLOCK_SIZE = 10;

export function StreetLayout({ scanData }: { scanData: any }) {
  const backendSpacing = scanData.layout?.spacing || 1.0;
  const gridWidth = Math.max(scanData.layout.width / backendSpacing, 16);
  const gridDepth = Math.max(scanData.layout.depth / backendSpacing, 16);
  
  const offsetX = -gridWidth / 2;
  const offsetZ = -gridDepth / 2;

  const elements = useMemo(() => {
    const items = [];

    // 1. Chão Gigante (O Asfalto Escuro)
    items.push(
      <mesh key="asphalt" position={[0, -0.05, 0]} rotation={[-Math.PI / 2, 0, 0]}>
        <planeGeometry args={[10000, 10000]} />
        <meshBasicMaterial color="#050505" />
      </mesh>
    );

    // 2. Grelha de Quarteirões, Passeios e Árvores
    for (let x = 0; x < gridWidth; x++) {
      for (let z = 0; z < gridDepth; z++) {
        const realX = (x + offsetX) * SCALE;
        const realZ = (z + offsetZ) * SCALE;

        // A) Passeio (Sidewalk) base do edifício (12.2x12.2m) 
        // Quase do tamanho do quarteirão (14), sobrando apenas 1.8m de estrada (Beco Apertado!)
        items.push(
          <mesh key={`sidewalk-${x}-${z}`} position={[realX, 0, realZ]} rotation={[-Math.PI / 2, 0, 0]}>
            <planeGeometry args={[BLOCK_SIZE + 0.2, BLOCK_SIZE + 0.2]} />
            <meshBasicMaterial color="#111111" />
            <lineSegments>
              <edgesGeometry args={[new THREE.PlaneGeometry(BLOCK_SIZE + 0.2, BLOCK_SIZE + 0.2)]} />
              <lineBasicMaterial color="#333333" />
            </lineSegments>
          </mesh>
        );

        // B) Marcas da Estrada (Dashed Lines)
        // Linha central ao longo do eixo Z (separador de faixas)
        if (x < gridWidth - 1) {
          const roadX = realX + SCALE / 2; // meio da estrada
          items.push(
            <mesh key={`road-mark-z-${x}-${z}`} position={[roadX, -0.04, realZ]} rotation={[-Math.PI / 2, 0, 0]}>
              <planeGeometry args={[0.2, SCALE * 0.8]} />
              <meshBasicMaterial color="#aaaaaa" />
            </mesh>
          );
        }

        // Linha central ao longo do eixo X (separador de faixas)
        if (z < gridDepth - 1) {
          const roadZ = realZ + SCALE / 2;
          items.push(
            <mesh key={`road-mark-x-${x}-${z}`} position={[realX, -0.04, roadZ]} rotation={[-Math.PI / 2, 0, Math.PI / 2]}>
              <planeGeometry args={[0.2, SCALE * 0.8]} />
              <meshBasicMaterial color="#aaaaaa" />
            </mesh>
          );
        }

        // C) Adereços de Estrada BEM ENQUADRADOS
        // Colocados exatamente a 0.2m FORA do limite do edifício (BLOCK_SIZE/2 = 5)
        const cornerX = realX + (BLOCK_SIZE / 2) + 0.2;
        const cornerZ = realZ + (BLOCK_SIZE / 2) + 0.2;
        
        // Apenas 30% de chance de ter um candeeiro nesta esquina
        if (Math.random() > 0.7) {
          items.push(
            <group key={`light-${x}-${z}`} position={[cornerX, 0, cornerZ]}>
              <mesh position={[0, 2, 0]}>
                <cylinderGeometry args={[0.05, 0.05, 4]} />
                <meshBasicMaterial color="#333333" />
              </mesh>
              <mesh position={[0.5, 4, 0]} rotation={[0, 0, Math.PI / 2]}>
                <cylinderGeometry args={[0.05, 0.05, 1]} />
                <meshBasicMaterial color="#333333" />
              </mesh>
              <mesh position={[0.8, 3.9, 0]}>
                <boxGeometry args={[0.3, 0.1, 0.1]} />
                <meshBasicMaterial color="#ffffff" toneMapped={false} />
              </mesh>
            </group>
          );
        }

        // Árvore geométrica alinhada no outro canto exterior do passeio
        // Apenas 15% de chance de nascer uma árvore
        const treeX = realX - (BLOCK_SIZE / 2) - 0.2;
        if (Math.random() > 0.85) {
          items.push(
            <group key={`tree-${x}-${z}`} position={[treeX, 0, cornerZ]}>
               <mesh position={[0, 1, 0]}>
                  <cylinderGeometry args={[0.1, 0.1, 2]} />
                  <meshBasicMaterial color="#00ffaa" wireframe={true} transparent opacity={0.3} toneMapped={false} />
                </mesh>
                <mesh position={[0, 2.5, 0]}>
                  <icosahedronGeometry args={[1, 1]} />
                  <meshBasicMaterial color="#00ffaa" wireframe={true} transparent opacity={0.5} toneMapped={false} />
                </mesh>
            </group>
          );
        }
      }
    }
    return items;
  }, [gridWidth, gridDepth]);

  return <group>{elements}</group>;
}

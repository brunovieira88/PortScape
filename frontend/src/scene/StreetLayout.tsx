import { useMemo } from 'react';
import * as THREE from 'three';

const SCALE = 13;
const BLOCK_SIZE = 10;

export function StreetLayout({ scanData, offsetX, offsetZ, gridWidth, gridDepth }: { scanData: any, offsetX: number, offsetZ: number, gridWidth: number, gridDepth: number }) {

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
    // Para cobrir a área em redor da câmara (0,0) sem quebrar o alinhamento com os prédios,
    // calculamos as coordenadas base (inteiras) que caem num raio de gridWidth/2 e gridDepth/2
    const startKx = Math.floor(-gridWidth / 2 - offsetX);
    const endKx = Math.ceil(gridWidth / 2 - offsetX);
    const startKz = Math.floor(-gridDepth / 2 - offsetZ);
    const endKz = Math.ceil(gridDepth / 2 - offsetZ);

    for (let kx = startKx; kx <= endKx; kx++) {
      for (let kz = startKz; kz <= endKz; kz++) {
        // Agora o lote fica EXATAMENTE alinhado com o edifício, mesmo que o offset
        // seja uma fração (ex: -1.5), porque aplicamos a mesma fórmula matemática
        const realX = (kx + offsetX) * SCALE;
        const realZ = (kz + offsetZ) * SCALE;

        // A) Passeio (Sidewalk)
        items.push(
          <mesh key={`sidewalk-${kx}-${kz}`} position={[realX, 0, realZ]} rotation={[-Math.PI / 2, 0, 0]}>
            <planeGeometry args={[BLOCK_SIZE + 0.2, BLOCK_SIZE + 0.2]} />
            <meshBasicMaterial color="#111111" />
            <lineSegments>
              <edgesGeometry args={[new THREE.PlaneGeometry(BLOCK_SIZE + 0.2, BLOCK_SIZE + 0.2)]} />
              <lineBasicMaterial color="#333333" />
            </lineSegments>
          </mesh>
        );

        // B) Marcas da Estrada (Dashed Lines)
        if (kx < endKx) {
          const roadX = realX + SCALE / 2; // meio da estrada
          items.push(
            <mesh key={`road-mark-z-${kx}-${kz}`} position={[roadX, -0.04, realZ]} rotation={[-Math.PI / 2, 0, 0]}>
              <planeGeometry args={[0.2, SCALE * 0.8]} />
              <meshBasicMaterial color="#aaaaaa" />
            </mesh>
          );
        }

        // Linha central ao longo do eixo X (separador de faixas)
        if (kz < endKz) {
          const roadZ = realZ + SCALE / 2;
          items.push(
            <mesh key={`road-mark-x-${kx}-${kz}`} position={[realX, -0.04, roadZ]} rotation={[-Math.PI / 2, 0, Math.PI / 2]}>
              <planeGeometry args={[0.2, SCALE * 0.8]} />
              <meshBasicMaterial color="#aaaaaa" />
            </mesh>
          );
        }

        // C) Adereços de Estrada BEM ENQUADRADOS
        const cornerX = realX + (BLOCK_SIZE / 2) + 0.2;
        const cornerZ = realZ + (BLOCK_SIZE / 2) + 0.2;
        
        if (Math.random() > 0.7) {
          items.push(
            <group key={`light-${kx}-${kz}`} position={[cornerX, 0, cornerZ]}>
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
      }
    }
    return items;
  }, [offsetX, offsetZ, gridWidth, gridDepth]);

  return <group>{elements}</group>;
}

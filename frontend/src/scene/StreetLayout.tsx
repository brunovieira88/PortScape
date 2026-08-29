import { useMemo } from 'react';
import * as THREE from 'three';

const SCALE = 22;
const BLOCK_SIZE = 10;

export function StreetLayout({ offsetX, offsetZ, gridWidth, gridDepth }: { offsetX: number, offsetZ: number, gridWidth: number, gridDepth: number }) {

  const elements = useMemo(() => {
    const items = [];

    items.push(
      <mesh key="asphalt" position={[0, -0.2, 0]} rotation={[-Math.PI / 2, 0, 0]}>
        <planeGeometry args={[10000, 10000]} />
        <meshBasicMaterial color="#050505" />
      </mesh>
    );

    // 2. Grelha de Quarteirões, Passeios e Árvores
    const startKx = Math.floor(-gridWidth / 2 - offsetX);
    const endKx = Math.ceil(gridWidth / 2 - offsetX);
    const startKz = Math.floor(-gridDepth / 2 - offsetZ);
    const endKz = Math.ceil(gridDepth / 2 - offsetZ);

    for (let kx = startKx; kx <= endKx; kx++) {
      for (let kz = startKz; kz <= endKz; kz++) {
        
        const realX = (kx + offsetX) * SCALE;
        const realZ = (kz + offsetZ) * SCALE;

        // A) Passeio (Sidewalk)
        items.push(
          <mesh key={`sidewalk-${kx}-${kz}`} position={[realX, -0.05, realZ]} rotation={[-Math.PI / 2, 0, 0]}>
            <planeGeometry args={[BLOCK_SIZE + 0.2, BLOCK_SIZE + 0.2]} />
            <meshBasicMaterial color="#111111" polygonOffset={true} polygonOffsetFactor={-1} polygonOffsetUnits={-1} />
            <lineSegments>
              <edgesGeometry args={[new THREE.PlaneGeometry(BLOCK_SIZE + 0.2, BLOCK_SIZE + 0.2)]} />
              <lineBasicMaterial color="#333333" linewidth={1} polygonOffset={true} polygonOffsetFactor={-2} polygonOffsetUnits={-2} />
            </lineSegments>
          </mesh>
        );

        // B) Marcas da Estrada (Road markings) - Linhas tracejadas
        const roadX = realX + (SCALE / 2);
        const roadZ = realZ + (SCALE / 2);

        // Linhas tracejadas verticais
        if (kx < endKx) {
          const numDashes = 5;
          const dashLength = (SCALE * 0.8) / (numDashes * 2);
          for (let i = 0; i < numDashes; i++) {
            const zOffset = -(SCALE * 0.4) + (i * 2 * dashLength) + (dashLength / 2);
            items.push(
              <mesh key={`road-mark-z-${kx}-${kz}-${i}`} position={[roadX, -0.1, realZ + zOffset]} rotation={[-Math.PI / 2, 0, 0]}>
                <planeGeometry args={[0.6, dashLength]} />
                <meshBasicMaterial color="#aaaaaa" depthWrite={false} polygonOffset={true} polygonOffsetFactor={-3} polygonOffsetUnits={-3} />
              </mesh>
            );
          }
        }

        // Linhas tracejadas horizontais
        if (kz < endKz) {
          const numDashes = 5;
          const dashLength = (SCALE * 0.8) / (numDashes * 2);
          for (let i = 0; i < numDashes; i++) {
            const xOffset = -(SCALE * 0.4) + (i * 2 * dashLength) + (dashLength / 2);
            items.push(
              <mesh key={`road-mark-x-${kx}-${kz}-${i}`} position={[realX + xOffset, -0.1, roadZ]} rotation={[-Math.PI / 2, 0, Math.PI / 2]}>
                <planeGeometry args={[0.6, dashLength]} />
                <meshBasicMaterial color="#aaaaaa" depthWrite={false} polygonOffset={true} polygonOffsetFactor={-3} polygonOffsetUnits={-3} />
              </mesh>
            );
          }
        }

        // D) Adereços de Estrada BEM ENQUADRADOS
        const cornerX = realX + (BLOCK_SIZE / 2) + 0.2;
        const cornerZ = realZ + (BLOCK_SIZE / 2) + 0.2;
        
        // Postes de Luz Normais
        if (Math.random() > 0.7) {
          items.push(
            <group key={`lamp-${kx}-${kz}`} position={[cornerX, 0, cornerZ]}>
              <mesh position={[0, 1.5, 0]}>
                <cylinderGeometry args={[0.02, 0.05, 3]} />
                <meshBasicMaterial color="#555555" />
              </mesh>
              <mesh position={[0.2, 3, 0]} rotation={[0, 0, Math.PI / 2]}>
                <cylinderGeometry args={[0.02, 0.02, 0.6]} />
                <meshBasicMaterial color="#555555" />
              </mesh>
              <mesh position={[0.4, 2.9, 0]}>
                <boxGeometry args={[0.2, 0.05, 0.1]} />
                <meshBasicMaterial color="#ffffff" />
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

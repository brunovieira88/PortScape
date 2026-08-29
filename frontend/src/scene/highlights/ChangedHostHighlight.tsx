import { useRef } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';

export function ChangedHostHighlight({ height = 40 }: { height?: number }) {
  const scannerRef = useRef<THREE.Mesh>(null);
  const gridRef = useRef<THREE.Mesh>(null);

  useFrame((state) => {
    const time = state.clock.getElapsedTime();
    if (scannerRef.current) {
      // Move up and down smoothly
      const normalizedSin = (Math.sin(time * 1.5) + 1) / 2; // 0 to 1
      scannerRef.current.position.y = 1 + normalizedSin * height;
    }
    if (gridRef.current) {
      gridRef.current.rotation.y = time * 0.2;
    }
  });

  return (
    <group>
      {/* Scanning laser ring that goes up and down */}
      <mesh ref={scannerRef} position={[0, 1, 0]} rotation={[-Math.PI / 2, 0, Math.PI / 4]}>
        <ringGeometry args={[9, 10, 4]} /> {/* Square-ish scanning ring aligned with building */}
        <meshBasicMaterial color="#ff8a00" transparent opacity={0.9} side={THREE.DoubleSide} blending={THREE.AdditiveBlending} depthWrite={false} />
      </mesh>
      
      {/* Ghostly grid cylinder wrapping the building */}
      <mesh ref={gridRef} position={[0, height / 2, 0]}>
        <cylinderGeometry args={[9, 9, height, 16, 8, true]} />
        <meshBasicMaterial color="#ff8a00" wireframe transparent opacity={0.15} blending={THREE.AdditiveBlending} depthWrite={false} />
      </mesh>

    </group>
  );
}

import { useRef } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';

export function NewHostHighlight() {
  const ringRef = useRef<THREE.Mesh>(null);
  const cylinderRef = useRef<THREE.Mesh>(null);

  useFrame((state) => {
    const time = state.clock.getElapsedTime();
    if (ringRef.current) {
      ringRef.current.rotation.z = time * 0.5;
      ringRef.current.scale.setScalar(1 + Math.sin(time * 3) * 0.1);
      (ringRef.current.material as THREE.MeshBasicMaterial).opacity = 0.5 + Math.sin(time * 5) * 0.3;
    }
    if (cylinderRef.current) {
      (cylinderRef.current.material as THREE.MeshBasicMaterial).opacity = 0.15 + Math.sin(time * 2) * 0.05;
      cylinderRef.current.position.y = 30 + Math.sin(time) * 2;
    }
  });

  return (
    <group>
      {/* Outer spinning ring */}
      <mesh ref={ringRef} position={[0, 0.5, 0]} rotation={[-Math.PI / 2, 0, 0]}>
        <ringGeometry args={[10.5, 12, 32]} />
        <meshBasicMaterial color="#fcee0a" transparent opacity={0.8} side={THREE.DoubleSide} blending={THREE.AdditiveBlending} depthWrite={false} />
      </mesh>
      
      {/* Inner stable ring */}
      <mesh position={[0, 0.6, 0]} rotation={[-Math.PI / 2, 0, 0]}>
        <ringGeometry args={[9.5, 10.2, 32]} />
        <meshBasicMaterial color="#ffffff" transparent opacity={0.4} side={THREE.DoubleSide} blending={THREE.AdditiveBlending} depthWrite={false} />
      </mesh>
      
      {/* Laser Column */}
      <mesh ref={cylinderRef} position={[0, 30, 0]}>
        <cylinderGeometry args={[11, 11, 60, 32, 1, true]} />
        <meshBasicMaterial color="#fcee0a" transparent opacity={0.15} side={THREE.DoubleSide} blending={THREE.AdditiveBlending} depthWrite={false} />
      </mesh>
      
    </group>
  );
}

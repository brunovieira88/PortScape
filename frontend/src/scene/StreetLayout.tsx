import { useLayoutEffect, useMemo, useRef } from 'react';
import type { ReactNode } from 'react';
import * as THREE from 'three';
import { BLOCK_SCALE as SCALE } from './cityGrid';

const BLOCK_SIZE = 10;
const SIDEWALK = BLOCK_SIZE + 0.2;
const DASHES_PER_ROAD = 5;

type Transform = { pos: [number, number, number], rot?: [number, number, number] };

/**
 * Desenha a mesma geometria em muitos sitios com uma so chamada de desenho.
 *
 * <p>A rua era o objecto mais pesado da aplicacao inteira, e por larga margem: 17
 * meshes por quarteirao -- passeio, dez tracos de estrada e adereços de esquina --
 * davam 4352 objectos num chao de 16x16, contra umas dezenas para os edificios todos.
 * Como as geometrias sao identicas e so mudam de sitio, instancia-las reduz isso a uma
 * mao-cheia de chamadas sem mudar nada do que se ve.
 */
function Instanced({ transforms, children }:
  { transforms: Transform[], children: ReactNode }) {

  const ref = useRef<THREE.InstancedMesh>(null);

  useLayoutEffect(() => {
    const mesh = ref.current;
    if (!mesh) { return; }
    const dummy = new THREE.Object3D();
    transforms.forEach((transform, i) => {
      dummy.position.set(...transform.pos);
      dummy.rotation.set(...(transform.rot ?? [0, 0, 0]));
      dummy.updateMatrix();
      mesh.setMatrixAt(i, dummy.matrix);
    });
    mesh.instanceMatrix.needsUpdate = true;
  }, [transforms]);

  if (transforms.length === 0) { return null; }

  return (
    <instancedMesh ref={ref} args={[undefined, undefined, transforms.length]}>
      {children}
    </instancedMesh>
  );
}

/** Decisao estavel por quarteirao -- substitui o Math.random que mudava a cada reload. */
function hashCell(kx: number, kz: number): number {
  const h = Math.imul(kx * 73856093 ^ kz * 19349663, 2654435761) >>> 0;
  return h / 0xffffffff;
}

export function StreetLayout({ offsetX, offsetZ, gridWidth, gridDepth }:
  { offsetX: number, offsetZ: number, gridWidth: number, gridDepth: number }) {

  const { sidewalks, dashes, lamps, kerbs } = useMemo(() => {
    const sidewalks: Transform[] = [];
    const dashes: Transform[] = [];
    const lamps: Transform[] = [];
    const lines: number[] = [];

    const startKx = Math.floor(-gridWidth / 2 - offsetX);
    const endKx = Math.ceil(gridWidth / 2 - offsetX);
    const startKz = Math.floor(-gridDepth / 2 - offsetZ);
    const endKz = Math.ceil(gridDepth / 2 - offsetZ);

    const dashLength = (SCALE * 0.8) / (DASHES_PER_ROAD * 2);
    const flat: [number, number, number] = [-Math.PI / 2, 0, 0];
    const half = SIDEWALK / 2;

    for (let kx = startKx; kx <= endKx; kx++) {
      for (let kz = startKz; kz <= endKz; kz++) {
        const realX = (kx + offsetX) * SCALE;
        const realZ = (kz + offsetZ) * SCALE;

        sidewalks.push({ pos: [realX, -0.05, realZ], rot: flat });

        // O contorno do passeio: em vez de um lineSegments por quarteirao, todos os
        // quarteiroes escrevem para o mesmo buffer e saem num unico objecto.
        const y = -0.045;
        const corners: [number, number][] = [
          [realX - half, realZ - half], [realX + half, realZ - half],
          [realX + half, realZ + half], [realX - half, realZ + half],
        ];
        for (let i = 0; i < 4; i++) {
          const [ax, az] = corners[i];
          const [bx, bz] = corners[(i + 1) % 4];
          lines.push(ax, y, az, bx, y, bz);
        }

        if (kx < endKx) {
          const roadX = realX + SCALE / 2;
          for (let i = 0; i < DASHES_PER_ROAD; i++) {
            const zOffset = -(SCALE * 0.4) + i * 2 * dashLength + dashLength / 2;
            dashes.push({ pos: [roadX, -0.1, realZ + zOffset], rot: flat });
          }
        }
        if (kz < endKz) {
          const roadZ = realZ + SCALE / 2;
          for (let i = 0; i < DASHES_PER_ROAD; i++) {
            const xOffset = -(SCALE * 0.4) + i * 2 * dashLength + dashLength / 2;
            dashes.push({ pos: [realX + xOffset, -0.1, roadZ], rot: [-Math.PI / 2, 0, Math.PI / 2] });
          }
        }

        if (hashCell(kx, kz) > 0.7) {
          lamps.push({ pos: [realX + BLOCK_SIZE / 2 + 0.2, 0, realZ + BLOCK_SIZE / 2 + 0.2] });
        }
      }
    }

    const kerbs = new THREE.BufferGeometry();
    kerbs.setAttribute('position', new THREE.Float32BufferAttribute(lines, 3));
    return { sidewalks, dashes, lamps, kerbs };
  }, [offsetX, offsetZ, gridWidth, gridDepth]);

  return (
    <group>
      <mesh position={[0, -0.2, 0]} rotation={[-Math.PI / 2, 0, 0]}>
        <planeGeometry args={[10000, 10000]} />
        {/* Iluminado para apanhar a queda da luz do ceu, e com nevoeiro: e o que faz
            o chao ao longe fundir-se com o horizonte sem costura. */}
        <meshStandardMaterial color="#0a0d16" roughness={0.75} metalness={0.2} />
      </mesh>

      <Instanced transforms={sidewalks}>
        <planeGeometry args={[SIDEWALK, SIDEWALK]} />
        <meshBasicMaterial color="#111111" polygonOffset polygonOffsetFactor={-1} polygonOffsetUnits={-1} />
      </Instanced>

      <lineSegments geometry={kerbs}>
        <lineBasicMaterial color="#333333" polygonOffset polygonOffsetFactor={-2} polygonOffsetUnits={-2} />
      </lineSegments>

      <Instanced transforms={dashes}>
        <planeGeometry args={[0.6, (SCALE * 0.8) / (DASHES_PER_ROAD * 2)]} />
        <meshBasicMaterial color="#aaaaaa" depthWrite={false} polygonOffset polygonOffsetFactor={-3} polygonOffsetUnits={-3} />
      </Instanced>

      {/* Cada peca do candeeiro e uma instancia sua: tres chamadas para todos eles. */}
      <Instanced transforms={lamps.map(l => ({ pos: [l.pos[0], 1.5, l.pos[2]] as [number, number, number] }))}>
        <cylinderGeometry args={[0.02, 0.05, 3]} />
        <meshBasicMaterial color="#555555" />
      </Instanced>
      <Instanced transforms={lamps.map(l => ({
        pos: [l.pos[0] + 0.2, 3, l.pos[2]] as [number, number, number],
        rot: [0, 0, Math.PI / 2] as [number, number, number],
      }))}>
        <cylinderGeometry args={[0.02, 0.02, 0.6]} />
        <meshBasicMaterial color="#555555" />
      </Instanced>
      <Instanced transforms={lamps.map(l => ({ pos: [l.pos[0] + 0.4, 2.9, l.pos[2]] as [number, number, number] }))}>
        <boxGeometry args={[0.2, 0.05, 0.1]} />
        <meshBasicMaterial color="#ffffff" toneMapped={false} />
      </Instanced>
    </group>
  );
}

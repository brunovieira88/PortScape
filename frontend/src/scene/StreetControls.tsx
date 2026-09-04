import { useRef, useEffect, useMemo } from 'react';
import type { Scan } from '../api/types';
import { useFrame, useThree } from '@react-three/fiber';
import * as THREE from 'three';
import { buildCityGrid, collidesAt, spawnPointFor, worldX, worldZ } from './cityGrid';
import { introFrame } from './cameraIntro';
import { stepDelta } from './frame';

interface TeleportRequest { ip: string; nonce: number; }

export function StreetControls({ scanData, teleportTarget }: { scanData: Scan, teleportTarget?: TeleportRequest | null }) {
  const { camera, gl } = useThree();
  const keys = useRef<{ [key: string]: boolean }>({});
  
  const velocity = useRef(new THREE.Vector3());
  const direction = useRef(new THREE.Vector3());
  // Reutilizados a cada frame em vez de realocados -- ver o mesmo motivo no Building.
  const movement = useRef(new THREE.Vector3());
  const UP = useRef(new THREE.Vector3(0, 1, 0));
  const targetRotation = useRef(new THREE.Euler(0, 0, 0, 'YXZ'));

  const STREET_Y = 1.7;

  const introTime = useRef(0);
  const isIntroPlaying = useRef(false);

  useEffect(() => {
    camera.rotation.order = 'YXZ';
    
    const handleKeyDown = (e: KeyboardEvent) => { keys.current[e.code] = true; };
    const handleKeyUp = (e: KeyboardEvent) => { keys.current[e.code] = false; };
    
    let isDragging = false;
    let prevMouseX = 0;
    let prevMouseY = 0;

    const handlePointerDown = (e: PointerEvent) => {
      isDragging = true;
      prevMouseX = e.clientX;
      prevMouseY = e.clientY;
      document.body.style.cursor = 'grabbing';
    };

    const handlePointerUp = () => {
      isDragging = false;
      document.body.style.cursor = 'auto';
    };

    const handlePointerMove = (e: PointerEvent) => {
      if (isDragging && !isIntroPlaying.current) { // Prevent looking around during intro
        const deltaX = e.clientX - prevMouseX;
        const deltaY = e.clientY - prevMouseY;
        
        targetRotation.current.y -= deltaX * 0.002;
        targetRotation.current.x -= deltaY * 0.002;
        
        // Limita o olhar para cima/baixo
        targetRotation.current.x = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, targetRotation.current.x));
        
        prevMouseX = e.clientX;
        prevMouseY = e.clientY;
      }
    };

    gl.domElement.addEventListener('pointerdown', handlePointerDown);
    window.addEventListener('pointerup', handlePointerUp);
    window.addEventListener('pointermove', handlePointerMove);
    window.addEventListener('keydown', handleKeyDown);
    window.addEventListener('keyup', handleKeyUp);

    return () => {
      gl.domElement.removeEventListener('pointerdown', handlePointerDown);
      window.removeEventListener('pointerup', handlePointerUp);
      window.removeEventListener('pointermove', handlePointerMove);
      window.removeEventListener('keydown', handleKeyDown);
      window.removeEventListener('keyup', handleKeyUp);
    };
  }, [camera, gl.domElement]);

  // A grelha vem do cityGrid, a mesma que desenha os edificios. Recalcula-la aqui foi
  // durante toda a fase 4 a razao de se atravessarem os predios e bater em paredes
  // invisiveis: este ficheiro usava SCALE 13 e centrava pela largura minima, enquanto a
  // cidade era desenhada a 22 e centrada pela largura real.
  const grid = useMemo(() => buildCityGrid(scanData), [scanData]);
  const checkCollision = (posX: number, posZ: number) => collidesAt(grid, posX, posZ);

  // Onde o voo aterra. Sai da mesma grelha que faz as colisoes, portanto e por
  // construcao um sitio onde se pode estar de pe -- ver o spawnPointFor.
  const spawn = useMemo(() => spawnPointFor(grid), [grid]);
  const landing = useRef(spawn);

  useEffect(() => {
    // Voo de Drone - Nasce no céu e olha para baixo
    landing.current = spawn;
    const start = introFrame(0, STREET_Y);
    camera.position.set(spawn.x, start.y, spawn.z + start.zOffset);
    targetRotation.current.set(start.pitch, 0, 0, 'YXZ');
    camera.rotation.copy(targetRotation.current);
    velocity.current.set(0, 0, 0);
    
    introTime.current = 0;
    isIntroPlaying.current = true;
  }, [scanData.id, camera, spawn]);

  // Teleporte a partir da lista de dispositivos: poe o jogador no sitio livre mais
  // proximo do edificio pedido, sem voo de chegada -- e um atalho, nao uma segunda
  // aterragem. O nonce garante que clicar duas vezes no mesmo host teleporta as duas.
  useEffect(() => {
    if (!teleportTarget) { return; }
    const host = [...grid.hosts, ...grid.ruins].find(h => h.ip === teleportTarget.ip);
    if (!host) { return; }

    const targetX = worldX(grid, host.gridX);
    const targetZ = worldZ(grid, host.gridZ);
    const spot = spawnPointFor(grid, { x: targetX, z: targetZ });

    isIntroPlaying.current = false;
    velocity.current.set(0, 0, 0);
    camera.position.set(spot.x, STREET_Y, spot.z);

    const dx = targetX - spot.x;
    const dz = targetZ - spot.z;
    if (Math.abs(dx) > 0.001 || Math.abs(dz) > 0.001) {
      targetRotation.current.y = Math.atan2(-dx, -dz);
    }
  }, [teleportTarget, grid, camera]);

  useFrame((_state, rawDelta) => {
    // Ver o stepDelta: o R3F passa o delta do relogio em bruto, e voltar a esta aba
    // depois de um minuto noutra dava um alfa de 600 nos lerps aqui em baixo.
    const delta = stepDelta(rawDelta);

    if (isIntroPlaying.current) {
      introTime.current += delta;
      const frame = introFrame(introTime.current, STREET_Y);

      camera.position.x = landing.current.x;
      camera.position.y = frame.y;
      camera.position.z = landing.current.z + frame.zOffset;
      targetRotation.current.x = frame.pitch;
      targetRotation.current.y = 0; // Ensures looking straight

      // Enquanto o voo decorre nao se acumula velocidade nenhuma: sem isto, teclas
      // carregadas durante a descida davam um solavanco no instante da aterragem.
      velocity.current.set(0, 0, 0);

      if (frame.done) {
        isIntroPlaying.current = false;
      }
    }

    camera.rotation.x = THREE.MathUtils.lerp(camera.rotation.x, targetRotation.current.x, 10 * delta);
    camera.rotation.y = THREE.MathUtils.lerp(camera.rotation.y, targetRotation.current.y, 10 * delta);
    camera.rotation.z = 0; 


    const moveZ = (keys.current['KeyS'] || keys.current['ArrowDown'] ? 1 : 0) - (keys.current['KeyW'] || keys.current['ArrowUp'] ? 1 : 0);
    const moveX = (keys.current['KeyD'] || keys.current['ArrowRight'] ? 1 : 0) - (keys.current['KeyA'] || keys.current['ArrowLeft'] ? 1 : 0);
    
    direction.current.set(moveX, 0, moveZ);
    if (direction.current.lengthSq() > 0.1) direction.current.normalize();

    const isBoosting = keys.current['ShiftLeft'] || keys.current['ShiftRight'];
    const speed = isBoosting ? 450.0 : 75.0; 
    const friction = 5.0; 
    
    if (moveZ !== 0 || moveX !== 0) {
      velocity.current.z += direction.current.z * speed * delta;
      velocity.current.x += direction.current.x * speed * delta;
    }
    
    velocity.current.x -= velocity.current.x * friction * delta;
    velocity.current.z -= velocity.current.z * friction * delta;

    if (Math.abs(velocity.current.x) < 0.001) velocity.current.x = 0;
    if (Math.abs(velocity.current.z) < 0.001) velocity.current.z = 0;

    const localMovement = movement.current
      .set(velocity.current.x * delta, 0, velocity.current.z * delta)
      .applyAxisAngle(UP.current, camera.rotation.y);

    let nextX = camera.position.x + localMovement.x;
    let nextZ = camera.position.z + localMovement.z;

    if (checkCollision(nextX, camera.position.z)) {
      velocity.current.x = 0; 
      nextX = camera.position.x;
    }
    if (checkCollision(nextX, nextZ)) {
      velocity.current.z = 0;
      nextZ = camera.position.z;
    }

    if (!isIntroPlaying.current) {
      camera.position.x = nextX;
      camera.position.z = nextZ;
      camera.position.y = STREET_Y;
    }
  });

  return null;
}

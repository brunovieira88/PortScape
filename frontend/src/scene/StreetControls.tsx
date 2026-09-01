import { useRef, useEffect, useMemo } from 'react';
import { useFrame, useThree } from '@react-three/fiber';
import * as THREE from 'three';
import { buildCityGrid, collidesAt } from './cityGrid';

export function StreetControls({ scanData }: { scanData: any }) {
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

  useEffect(() => {
    // Voo de Drone - Nasce no céu e olha para baixo
    camera.position.set(0, 150, 40);
    targetRotation.current.set(-Math.PI / 4, 0, 0, 'YXZ');
    camera.rotation.copy(targetRotation.current);
    velocity.current.set(0, 0, 0);
    
    introTime.current = 0;
    isIntroPlaying.current = true;
  }, [scanData.id, camera]);

  // A grelha vem do cityGrid, a mesma que desenha os edificios. Recalcula-la aqui foi
  // durante toda a fase 4 a razao de se atravessarem os predios e bater em paredes
  // invisiveis: este ficheiro usava SCALE 13 e centrava pela largura minima, enquanto a
  // cidade era desenhada a 22 e centrada pela largura real.
  const grid = useMemo(() => buildCityGrid(scanData), [scanData]);
  const checkCollision = (posX: number, posZ: number) => collidesAt(grid, posX, posZ);

  useFrame((_state, delta) => {
    if (isIntroPlaying.current) {
      introTime.current += delta;
      const progress = Math.min(introTime.current / 3.0, 1);
      
      // Easing out cubic: smooth deceleration as it approaches the ground
      const easeProgress = 1 - Math.pow(1 - progress, 3);
      
      // Interpolate Y from 150 to STREET_Y
      camera.position.y = 150 * (1 - easeProgress) + STREET_Y * easeProgress;
      
      // Interpolate Z from 40 to 0 (move forward while dropping)
      camera.position.z = 40 * (1 - easeProgress);
      
      // Interpolate rotation from looking down (-Math.PI / 4) to straight ahead (0)
      targetRotation.current.x = (-Math.PI / 4) * (1 - easeProgress); 
      targetRotation.current.y = 0; // Ensures looking straight

      if (progress >= 1) {
        isIntroPlaying.current = false;
        camera.position.y = STREET_Y;
        camera.position.z = 0;
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

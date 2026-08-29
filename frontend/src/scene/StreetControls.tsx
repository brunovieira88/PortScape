import { useRef, useEffect } from 'react';
import { useFrame, useThree } from '@react-three/fiber';
import * as THREE from 'three';

export function StreetControls({ scanData }: { scanData: any }) {
  const { camera, gl } = useThree();
  const keys = useRef<{ [key: string]: boolean }>({});
  
  const velocity = useRef(new THREE.Vector3());
  const direction = useRef(new THREE.Vector3());
  const targetRotation = useRef(new THREE.Euler(0, 0, 0, 'YXZ'));

  const STREET_Y = 1.7;

  useEffect(() => {
    camera.rotation.order = 'YXZ';
    camera.position.set(0, STREET_Y, 0);
    targetRotation.current.copy(camera.rotation);

    const handleKeyDown = (e: KeyboardEvent) => { keys.current[e.code] = true; };
    const handleKeyUp = (e: KeyboardEvent) => { keys.current[e.code] = false; };
    
    let isDragging = false;
    let prevMouseX = 0;
    let prevMouseY = 0;

    const handleMouseDown = (e: MouseEvent) => { 
      isDragging = true; 
      prevMouseX = e.clientX;
      prevMouseY = e.clientY;
    };
    
    const handleMouseUp = () => { isDragging = false; };
    
    const handleMouseMove = (e: MouseEvent) => {
      if (!isDragging) return;
      
      const deltaX = e.clientX - prevMouseX;
      const deltaY = e.clientY - prevMouseY;
      
      targetRotation.current.y -= deltaX * 0.005;
      targetRotation.current.x -= deltaY * 0.005;
      targetRotation.current.x = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, targetRotation.current.x));

      prevMouseX = e.clientX;
      prevMouseY = e.clientY;
    };

    window.addEventListener('keydown', handleKeyDown);
    window.addEventListener('keyup', handleKeyUp);
    gl.domElement.addEventListener('mousedown', handleMouseDown);
    window.addEventListener('mouseup', handleMouseUp);
    window.addEventListener('mousemove', handleMouseMove);

    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      window.removeEventListener('keyup', handleKeyUp);
      gl.domElement.removeEventListener('mousedown', handleMouseDown);
      window.removeEventListener('mouseup', handleMouseUp);
      window.removeEventListener('mousemove', handleMouseMove);
    };
  }, [camera, gl.domElement]);

  // Teleporta o jogador de volta para o centro seguro (0,0) sempre que o mapa for reconstruído!
  // Evita ficar preso "fora" das paredes invisíveis se o novo scan for mais pequeno.
  useEffect(() => {
    camera.position.set(0, STREET_Y, 0);
    velocity.current.set(0, 0, 0);
  }, [scanData.id, camera]); // Só corre quando o ID do scan muda (ou seja, novo mapa carregado)

  const SCALE = 13;
  const backendSpacing = scanData.layout?.spacing || 1.0;
  const gridWidth = Math.max(scanData.layout.width / backendSpacing, 16);
  const gridDepth = Math.max(scanData.layout.depth / backendSpacing, 16);
  
  const offsetX = -gridWidth / 2;
  const offsetZ = -gridDepth / 2;
  const minCityX = (0 + offsetX) * SCALE - SCALE/2;
  const maxCityX = (gridWidth - 1 + offsetX) * SCALE + SCALE/2;
  const minCityZ = (0 + offsetZ) * SCALE - SCALE/2;
  const maxCityZ = (gridDepth - 1 + offsetZ) * SCALE + SCALE/2;

  const checkCollision = (posX: number, posZ: number) => {
    if (posX < minCityX || posX > maxCityX || posZ < minCityZ || posZ > maxCityZ) return true;
    const gridX = posX / SCALE - offsetX;
    const gridZ = posZ / SCALE - offsetZ;
    const cellsToCheck = [
      [Math.floor(gridX), Math.floor(gridZ)],
      [Math.ceil(gridX), Math.floor(gridZ)],
      [Math.floor(gridX), Math.ceil(gridZ)],
      [Math.ceil(gridX), Math.ceil(gridZ)],
    ];
    for (let [gx, gz] of cellsToCheck) {
      const hasBuilding = scanData.hosts.some((h: any) => (h.position.x / backendSpacing) === gx && (h.position.z / backendSpacing) === gz);
      if (hasBuilding) {
        const centerX = (gx + offsetX) * SCALE;
        const centerZ = (gz + offsetZ) * SCALE;
        const bound = 6.0; 
        if (Math.abs(posX - centerX) < bound && Math.abs(posZ - centerZ) < bound) return true;
      }
    }
    return false;
  };

  useFrame((_state, delta) => {
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

    const localMovement = new THREE.Vector3(velocity.current.x * delta, 0, velocity.current.z * delta);
    localMovement.applyAxisAngle(new THREE.Vector3(0, 1, 0), camera.rotation.y);

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

    camera.position.x = nextX;
    camera.position.z = nextZ;
    camera.position.y = STREET_Y;
  });

  return null;
}

import { useRef, useEffect, useState } from 'react';
import { useFrame, useThree } from '@react-three/fiber';
import { Html } from '@react-three/drei';
import * as THREE from 'three';
import { Architecture } from './buildings/ArchitectureBuilder';
import { NewHostHighlight } from './highlights/NewHostHighlight';
import { ChangedHostHighlight } from './highlights/ChangedHostHighlight';

interface BuildingProps {
  label: string;
  x: number;
  z: number;
  portCount: number;
  riskBand: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'UNKNOWN';
  isRuin: boolean;
  onClick: () => void;
  isSelected?: boolean;
  hostData?: any;
  onClose?: () => void;
  onOpenDetails?: () => void;
  isNew?: boolean;
  isChanged?: boolean;
}

export const BAND_COLORS = {
  CRITICAL: '#ff003c', // Cyberpunk Red
  HIGH: '#ff8a00',     // Neon Orange
  MEDIUM: '#fcee0a',   // Electric Yellow
  LOW: '#00f0ff',      // Cyan
  UNKNOWN: '#808080'   // Dim Gray
};

export function Building({ label, x, z, portCount, riskBand, isRuin, onClick, isSelected, hostData, onClose, onOpenDetails, isNew, isChanged }: BuildingProps) {
  const groupRef = useRef<THREE.Group>(null);
  const { camera } = useThree();
  
  const [isNear, setIsNear] = useState(false);
  const [forceClosed, setForceClosed] = useState(false); 
  const [spawnOffset, setSpawnOffset] = useState<[number, number]>([0, 6]); // [x, z] offset
  
  const targetScale = 1;
  
  useEffect(() => {
    console.log(`Building mounted: ${label} at x:${x}, z:${z}, isRuin:${isRuin}`);
    if (groupRef.current) groupRef.current.scale.y = 0.01;
  }, []);

  const MAX_INTERACT_DISTANCE = 45; 
  const AUTO_SPAWN_ENTER = 18; 
  const AUTO_SPAWN_LEAVE = 22; 
  
  useFrame((_state, delta) => {
    if (groupRef.current) {
      groupRef.current.scale.y = THREE.MathUtils.lerp(groupRef.current.scale.y, targetScale, 4 * delta);
    }

    const dist = camera.position.distanceTo(new THREE.Vector3(x, 0, z));

    if (!isNear && dist < AUTO_SPAWN_ENTER) {
      // Quando abre por proximidade, calcula o lado virado para o jogador
      const dir = camera.position.clone().sub(new THREE.Vector3(x, 0, z)).setY(0).normalize();
      setSpawnOffset([dir.x * 6, dir.z * 6]);
      setIsNear(true);
    } else if (isNear && dist > AUTO_SPAWN_LEAVE) {
      setIsNear(false);
      setForceClosed(false); 
    }

    if (isSelected && onClose && dist > MAX_INTERACT_DISTANCE) {
      onClose();
    }
  });

  const isTower = portCount > 3;
  const baseH = isTower ? Math.min(portCount, 25) * 3 : 8;
  const roofExtra = isTower ? (portCount >= 8 ? 18 : 5) : 4;
  const waypointY = baseH + roofExtra + 2; 
  const color = BAND_COLORS[riskBand] || BAND_COLORS.UNKNOWN;

  const handleClick = (e: any) => {
    e.stopPropagation();
    const dist = camera.position.distanceTo(new THREE.Vector3(x, 0, z));
    if (dist <= MAX_INTERACT_DISTANCE) {
      // Quando abre por clique, calcula o lado onde o utilizador está
      const dir = camera.position.clone().sub(new THREE.Vector3(x, 0, z)).setY(0).normalize();
      setSpawnOffset([dir.x * 6, dir.z * 6]);
      onClick();
    }
  };

  const handlePointerOver = (e: any) => {
    e.stopPropagation();
    const dist = camera.position.distanceTo(new THREE.Vector3(x, 0, z));
    document.body.style.cursor = dist <= MAX_INTERACT_DISTANCE ? 'pointer' : 'not-allowed';
  };

  const showUI = (isSelected || (isNear && !forceClosed)) && hostData;

  const handleClose = (e: any) => {
    e.stopPropagation(); 
    if (onClose) onClose();
    if (isNear) setForceClosed(true); 
  };

  let cleanHostname: string | undefined = undefined;
  if (hostData?.hostname && typeof hostData.hostname === 'string') {
    const stripped = hostData.hostname.replace(/\.(home|lan|local)$/i, '').trim();
    if (stripped !== '' && stripped.toLowerCase() !== 'null') {
      cleanHostname = stripped;
    }
  }

  return (
    <group 
      ref={groupRef} 
      position={[x, 0, z]}
      onClick={handleClick}
      onPointerOver={handlePointerOver}
      onPointerOut={() => document.body.style.cursor = 'auto'}
    >
      <Architecture 
        portCount={portCount} 
        color={color} 
        isRuin={isRuin} 
        riskBand={riskBand}
      />
      {isNew && !isRuin && <NewHostHighlight />}
      {isChanged && !isRuin && <ChangedHostHighlight height={baseH + roofExtra + 5} />}
      
      {showUI && (
        <>
          <mesh position={[spawnOffset[0], 0.2, spawnOffset[1]]} rotation={[Math.PI / 2, 0, 0]}>
            <ringGeometry args={[0.5, 0.8, 16]} />
            <meshBasicMaterial color={color} transparent opacity={0.4} side={THREE.DoubleSide} />
          </mesh>

          <mesh position={[spawnOffset[0], 2.5, spawnOffset[1]]}>
            <cylinderGeometry args={[0.02, 0.02, 5, 8]} />
            <meshBasicMaterial color={color} transparent opacity={0.6} />
          </mesh>

          <Html 
            key={`html-panel-${label}`}
            position={[spawnOffset[0], 5, spawnOffset[1]]} 
            center 
            zIndexRange={[100, 0]}
            distanceFactor={10}
          >
            <style>
              {`
                @keyframes hologramExpand {
                  0% { transform: scaleY(0.01) scaleX(0); opacity: 0; filter: contrast(500%) brightness(500%); }
                  50% { transform: scaleY(0.01) scaleX(1); opacity: 0.8; filter: contrast(200%) brightness(200%); }
                  100% { transform: scaleY(1) scaleX(1); opacity: 1; filter: contrast(100%) brightness(100%); }
                }
                .hologram-panel {
                  animation: hologramExpand 0.3s cubic-bezier(0.16, 1, 0.3, 1) forwards;
                  transform-origin: bottom center;
                }
              `}
            </style>

            <div className="relative hologram-panel">
              <div className="w-80 bg-[#030d12]/80 backdrop-blur-2xl border border-[#00f0ff]/20 shadow-[0_0_30px_rgba(0,240,255,0.15)] text-white font-sans select-none rounded-2xl overflow-hidden">
              
              {/* Header */}
              <div className="flex justify-between items-center bg-white/5 p-4 border-b border-white/10">
                <div>
                  <div className="text-xs text-[#00f0ff] tracking-widest uppercase mb-1">
                    {hostData.ip || 'Target IP'}
                  </div>
                  <div className="text-xl font-bold tracking-wider">{cleanHostname || hostData.ip}</div>
                </div>
                {/* OS ICON / BADGE */}
                {(hostData.osGuess && typeof hostData.osGuess === 'string' && hostData.osGuess.trim().toLowerCase() !== 'null') && (
                  <div className="text-right">
                    <div className="text-[10px] text-gray-500 uppercase tracking-widest">OS DETECTED</div>
                    <div className="text-sm text-white font-mono">{hostData.osGuess}</div>
                  </div>
                )}
                <button 
                  className="text-[#00f0ff]/50 hover:text-[#00f0ff] cursor-pointer text-2xl"
                  onClick={handleClose}
                >
                  ✕
                </button>
              </div>

              {/* Corpo */}
              <div className="p-5">
                <div className="flex justify-between items-end mb-5 border-b border-white/10 pb-5">
                  <div>
                    <div className="text-xs text-gray-500 uppercase mb-1">Risk Level</div>
                    <div className="text-3xl font-black tracking-widest drop-shadow-md" style={{ color }}>{hostData.riskBand}</div>
                  </div>
                  <div className="text-right">
                    <div className="text-xs text-gray-500 uppercase mb-1">Score</div>
                    <div className="text-3xl font-light">{hostData.riskScore}</div>
                  </div>
                </div>

                <div className="text-xs text-gray-400 font-bold uppercase mb-3 tracking-wider">Open Ports ({hostData.ports?.length || 0})</div>
                <div className="max-h-56 overflow-y-auto space-y-2 pr-2">
                  {hostData.ports?.map((port: any, idx: number) => (
                    <div key={idx} className="flex justify-between items-center py-2 border-b border-white/10 last:border-0 hover:bg-white/5 px-2 -mx-2 transition-colors">
                      <div className="flex items-center gap-3">
                        <span className="text-[#00f0ff] text-base font-bold w-12">{port.port}</span>
                        <span className="text-xs text-gray-500 font-bold uppercase">{port.protocol}</span>
                      </div>
                      <div className="text-sm text-gray-100 uppercase tracking-widest">{port.service}</div>
                    </div>
                  ))}
                </div>

                <div className="mt-6 border-t border-[#00f0ff]/20 pt-4">
                  <button 
                    onClick={(e) => { e.stopPropagation(); if (onOpenDetails) onOpenDetails(); }}
                    className="w-full bg-[#00f0ff]/10 hover:bg-[#00f0ff]/30 text-[#00f0ff] border border-[#00f0ff]/50 py-3 rounded-lg text-xs font-bold tracking-[0.2em] uppercase transition-all shadow-[0_0_10px_rgba(0,240,255,0.1)] hover:shadow-[0_0_20px_rgba(0,240,255,0.3)]"
                  >
                    View Complete Audit Logs
                  </button>
                </div>
              </div>
            </div>
          </div>
          </Html>
        </>
      )}

      {/* Waypoint DOM (Imune ao Bloom/Luzes do ambiente 3D) */}
      {!showUI && (
        <Html key={`html-waypoint-${label}`} position={[0, waypointY, 0]} center zIndexRange={[50, 0]}>
          <div 
            className="flex flex-col items-center justify-center pointer-events-none select-none text-center whitespace-nowrap"
            style={{ 
              textShadow: `-1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000, 1px 1px 0 #000, 0 0 10px ${color}, 0 0 20px ${color}`
            }}
          >
            <div className="text-white font-bold tracking-widest text-sm">{cleanHostname || label}</div>
            <div className="text-white/90 font-bold uppercase text-[10px] mt-0.5">
              {isRuin ? "OFFLINE" : `${portCount} PORTS`}
            </div>
          </div>
        </Html>
      )}
    </group>
  );
}

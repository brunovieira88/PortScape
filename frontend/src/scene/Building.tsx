import { useRef, useEffect, useMemo, useState } from 'react';
import { useFrame, useThree } from '@react-three/fiber';
import { Html } from '@react-three/drei';
import * as THREE from 'three';
import { Architecture, DETAIL, type DetailLevel } from './buildings/ArchitectureBuilder';
import { buildingHeight, footprintRadius, seedOf } from './buildings/towerForm';
import { deviceKindOf } from './buildings/deviceKind';
import { stepDelta } from './frame';
import { NewHostHighlight } from './highlights/NewHostHighlight';
import { ChangedHostHighlight } from './highlights/ChangedHostHighlight';

interface BuildingProps {
  label: string;
  x: number;
  z: number;
  portCount: number;
  riskBand: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'UNKNOWN';
  vendor?: string | null;
  isRuin: boolean;
  onClick: () => void;
  isSelected?: boolean;
  hostData?: any;
  onClose?: () => void;
  onOpenDetails?: () => void;
  isNew?: boolean;
  isChanged?: boolean;
}

/**
 * A paleta das faixas de risco.
 *
 * <p>Os valores estao equilibrados por <b>luminancia percebida</b>, nao escolhidos a
 * olho. Um amarelo saturado e cerca de quatro vezes mais luminoso do que um vermelho
 * saturado -- isso e perceptual, nao e afinacao -- e com o bloom a partir de 0.2 o
 * amarelo ultrapassava o limiar em 39 vezes o que o vermelho ultrapassava. O resultado
 * era a hierarquia ao contrario: o MEDIUM dominava a cidade e o CRITICAL desaparecia.
 *
 * <p>O CRITICAL e o HIGH ficam nos tons originais; o MEDIUM e o LOW foram descidos ate
 * onde ainda se leem como amarelo e ciano. Nao da para os igualar ao vermelho sem os
 * transformar em azeitona e petroleo, por isso o amarelo continua a ser o mais claro --
 * so que por uma margem que ja nao rouba a cena ao vermelho.
 */
export const BAND_COLORS = {
  CRITICAL: '#ff003c', // vermelho — luminancia 0.22
  HIGH: '#ff8a00',     // laranja   — 0.39
  MEDIUM: '#c1b602',   // amarelo   — 0.45, era 0.82
  LOW: '#00b7c3',      // ciano     — 0.38, era 0.70
  UNKNOWN: '#595959'   // cinzento  — 0.10, era 0.22: "nao avaliado" nao deve chamar
};

/**
 * A cor da faixa, afastada um pouco consoante o host.
 *
 * <p>Todos os edificios de uma faixa partilhavam o mesmo hexadecimal exato, o que faz
 * um bairro ler como uma mancha unica de cor em vez de um conjunto de edificios. O
 * desvio e pequeno de proposito -- tem de continuar a ser obvio a que faixa pertence,
 * porque a cor e informacao. Muda o tom e o brilho, nunca ao ponto de trocar de faixa.
 */
function shadeFor(band: keyof typeof BAND_COLORS, seed: number): string {
  const base = new THREE.Color(BAND_COLORS[band] || BAND_COLORS.UNKNOWN);
  const hueShift = ((seed % 1000) / 1000 - 0.5) * 0.035;
  const lightShift = (((seed >> 10) % 1000) / 1000 - 0.5) * 0.22;
  return base.offsetHSL(hueShift, 0, lightShift).getStyle();
}

export function Building({ label, x, z, portCount, riskBand, vendor, isRuin, onClick, isSelected, hostData, onClose, onOpenDetails, isNew, isChanged }: BuildingProps) {
  const groupRef = useRef<THREE.Group>(null);
  const { camera } = useThree();
  
  const [isNear, setIsNear] = useState(false);
  const [detail, setDetail] = useState<DetailLevel>(DETAIL.FULL);
  const [forceClosed, setForceClosed] = useState(false); 
  const [spawnOffset, setSpawnOffset] = useState<[number, number]>([0, 6]); // [x, z] offset
  
  const targetScale = 1;

  // A posicao do edificio nao muda: alocar um Vector3 por frame para a mesma conta
  // punha ~30 mil objetos por segundo no colector de lixo, num /24.
  const anchor = useMemo(() => new THREE.Vector3(x, 0, z), [x, z]);
  
  useEffect(() => {
    // Nasce achatado e cresce -- ver o lerp da escala no useFrame.
    if (groupRef.current) groupRef.current.scale.y = 0.01;
  }, []);

  const MAX_INTERACT_DISTANCE = 45; 
  const AUTO_SPAWN_ENTER = 18; 
  const AUTO_SPAWN_LEAVE = 22; 

  // Distancias a que o edificio ganha e perde detalhe. A margem entre entrar e sair
  // evita que um edificio a oscilar sobre o limiar remonte a geometria a cada frame.
  const FULL_DETAIL_IN = 120;
  const FULL_DETAIL_OUT = 150;
  const STRUCTURE_IN = 300;
  const STRUCTURE_OUT = 350;
  
  useFrame((_state, rawDelta) => {
    // Ver o stepDelta: sem o limite, voltar a esta aba estica os edificios ao ceu.
    const delta = stepDelta(rawDelta);
    if (groupRef.current) {
      groupRef.current.scale.y = THREE.MathUtils.lerp(groupRef.current.scale.y, targetScale, 4 * delta);
    }

    const dist = camera.position.distanceTo(anchor);

    // So se escreve no estado quando o nivel muda mesmo -- um setState por frame
    // recriava a geometria toda continuamente, que era pior do que nao ter LOD.
    const next: DetailLevel =
      dist < (detail >= DETAIL.FULL ? FULL_DETAIL_OUT : FULL_DETAIL_IN) ? DETAIL.FULL
        : dist < (detail >= DETAIL.STRUCTURE ? STRUCTURE_OUT : STRUCTURE_IN) ? DETAIL.STRUCTURE
          : DETAIL.SILHOUETTE;
    if (next !== detail) {
      setDetail(next);
    }

    if (!isNear && dist < AUTO_SPAWN_ENTER) {
      // Quando abre por proximidade, calcula o lado virado para o jogador
      const dir = camera.position.clone().sub(anchor).setY(0).normalize();
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

  const seed = seedOf(label);
  const color = shadeFor(riskBand, seed);
  // A altura vem do towerForm, o mesmo modulo que desenha o edificio. Duplicar a
  // formula aqui punha a etiqueta cinco unidades acima do telhado de todas as casas.
  const kind = deviceKindOf(vendor);
  const height = buildingHeight(portCount, seed, kind);
  // Os destaques envolvem o edificio: o raio tem de vir da planta dele, nao de um
  // numero fixo. Ver a nota no ChangedHostHighlight.
  const radius = footprintRadius(portCount, seed, kind);
  const waypointY = height + 2;

  const handleClick = (e: any) => {
    e.stopPropagation();
    const dist = camera.position.distanceTo(anchor);
    if (dist <= MAX_INTERACT_DISTANCE) {
      // Quando abre por clique, calcula o lado onde o utilizador está
      const dir = camera.position.clone().sub(anchor).setY(0).normalize();
      setSpawnOffset([dir.x * 6, dir.z * 6]);
      onClick();
    }
  };

  const handlePointerOver = (e: any) => {
    e.stopPropagation();
    const dist = camera.position.distanceTo(anchor);
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
        kind={kind}
        detail={detail}
        seed={seed}
      />
      {isNew && !isRuin && <NewHostHighlight radius={radius} />}
      {isChanged && !isRuin && <ChangedHostHighlight radius={radius} />}
      
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
                  {hostData.vendor && (
                    <div className="text-[10px] text-gray-400 tracking-wide mt-0.5">{hostData.vendor}</div>
                  )}
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
                        <span className="text-[#00f0ff] text-base font-bold w-12">{port.number}</span>
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
      {/* A etiqueta e um no de DOM que o drei reposiciona a cada frame. Uma por
          edificio de um /24 sao 254 divs a competir com a cena; longe nem se le. */}
      {!showUI && detail >= DETAIL.STRUCTURE && (
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
            {/* O fio no chao so se ve de perto e de cima. A etiqueta ja esta a flutuar
                por cima do edificio e le-se de longe: e o sitio barato de dizer o
                estado, sem mais nada dentro da cena a competir com os edificios. */}
            {!isRuin && (isNew || isChanged) && (
              <div className="text-white/60 uppercase text-[8px] tracking-[0.25em] mt-0.5">
                {isNew ? '· NEW ·' : '· CHANGED ·'}
              </div>
            )}
          </div>
        </Html>
      )}
    </group>
  );
}

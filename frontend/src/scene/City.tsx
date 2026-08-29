import { EffectComposer, Bloom } from '@react-three/postprocessing';
import { Stars } from '@react-three/drei';
import * as THREE from 'three';
import { useMemo } from 'react';
import { Building } from './Building';
import { StreetControls } from './StreetControls';
import { StreetLayout } from './StreetLayout';



// SCALE define a distância total entre os edifícios. Baixado de 13 para 11.5 para aproximar mais os bairros.
const SCALE = 22; 

import { useFrame } from '@react-three/fiber';
import { useRef } from 'react';

interface CityProps {
  scanData: any;
  selectedHost: any;
  onSelectHost: (host: any) => void;
  onOpenDetails: (host: any) => void;
}

function Skybox() {
  const skyRef = useRef<THREE.Group>(null);
  
  // A magia para fazer o céu parecer infinito:
  // Em todos os frames, movemos a cúpula do céu para a exata posição da câmara!
  useFrame(({ camera }) => {
    if (skyRef.current) {
      skyRef.current.position.copy(camera.position);
      skyRef.current.updateMatrixWorld();
    }
  });

  return (
    <group ref={skyRef}>
      {/* O raio volta a 300 para garantir que o tamanho dos pontos é visível e não sofre culling.
          O factor aumenta para 15 para evitar sub-pixel flickering (estrelas a piscar) */}
      <Stars radius={300} depth={150} count={4000} factor={15} saturation={1} fade speed={0} />
      <CrescentMoon />
    </group>
  );
}

function CrescentMoon() {
  const moonTexture = useMemo(() => {
    const canvas = document.createElement('canvas');
    canvas.width = 256;
    canvas.height = 256;
    const ctx = canvas.getContext('2d');
    if (ctx) {
      // Desenha a lua cheia branca
      ctx.fillStyle = '#ffffff';
      ctx.beginPath();
      ctx.arc(128, 128, 110, 0, Math.PI * 2);
      ctx.fill();
      
      // Apaga (recorta) a parte interior usando composição de pixels
      ctx.globalCompositeOperation = 'destination-out';
      ctx.beginPath();
      // Desloca o apagador para a direita e para cima para formar a foice
      ctx.arc(178, 88, 110, 0, Math.PI * 2);
      ctx.fill();
    }
    const texture = new THREE.CanvasTexture(canvas);
    texture.anisotropy = 16;
    return texture;
  }, []);

  return (
    <group position={[300, 200, -800]} rotation={[0, -Math.PI / 8, Math.PI / 6]}>
      <mesh>
        <planeGeometry args={[120, 120]} />
        {/* toneMapped={false} garante que brilha imenso e não fica cinzento */}
        <meshBasicMaterial 
          map={moonTexture} 
          transparent={true} 
          toneMapped={false} 
          color="#ffffff" 
        />
      </mesh>
    </group>
  );
}

export function City({ scanData, selectedHost, onSelectHost, onOpenDetails }: CityProps) {
  const backendSpacing = scanData.layout?.spacing || 1.0;
  
  // COMPACT_FACTOR (Ex: 2.0): Força as coordenadas flutuantes do backend a dividirem-se
  // por um número maior, o que resulta em menos "blocos vazios" (lotes) de alcatrão entre os hosts.
  const COMPACT_FACTOR = 2.0;
  const effectiveSpacing = backendSpacing * COMPACT_FACTOR;
  
  // Anti-collision Grid Packer
  const { processedHosts, processedRuins } = useMemo(() => {
    const occupied = new Set<string>();
    
    const findEmptyCell = (startX: number, startZ: number) => {
      if (!occupied.has(`${startX},${startZ}`)) return { x: startX, z: startZ };
      let radius = 1;
      while (radius < 50) {
        for (let dx = -radius; dx <= radius; dx++) {
          for (let dz = -radius; dz <= radius; dz++) {
            if (Math.abs(dx) === radius || Math.abs(dz) === radius) {
              const nx = startX + dx;
              const nz = startZ + dz;
              if (!occupied.has(`${nx},${nz}`)) return { x: nx, z: nz };
            }
          }
        }
        radius++;
      }
      return { x: startX, z: startZ };
    };

    const hosts = (scanData.hosts || []).map((h: any) => {
      const gx = Math.round(h.position.x / effectiveSpacing);
      const gz = Math.round(h.position.z / effectiveSpacing);
      const cell = findEmptyCell(gx, gz);
      occupied.add(`${cell.x},${cell.z}`);
      return { ...h, gridX: cell.x, gridZ: cell.z };
    });

    const ruins = (scanData.ruins || []).map((r: any) => {
      const gx = Math.round(r.position.x / effectiveSpacing);
      const gz = Math.round(r.position.z / effectiveSpacing);
      const cell = findEmptyCell(gx, gz);
      occupied.add(`${cell.x},${cell.z}`);
      return { ...r, gridX: cell.x, gridZ: cell.z };
    });

    return { processedHosts: hosts, processedRuins: ruins };
  }, [scanData, effectiveSpacing]);

  // Tamanho real dos edifícios calculados pelo backend
  const layoutW = scanData.layout.width / effectiveSpacing;
  const layoutD = scanData.layout.depth / effectiveSpacing;
  
  // O offset dos edifícios DEVE ser o centro exato da bounding box deles
  // para que a câmara no (0,0) nasça sempre no meio da cidade!
  const offsetX = -layoutW / 2;
  const offsetZ = -layoutD / 2;

  // Forçamos o asfalto (chão) a ter pelo menos 16x16 quarteirões
  // Mas já não usamos isto para mover os edifícios!
  const gridWidth = Math.max(layoutW, 16);
  const gridDepth = Math.max(layoutD, 16);

  return (
    <>
      <StreetControls scanData={scanData} />
      <ambientLight intensity={0.1} />

      {/* Céu Cyberpunk: Estrelas e Lua infinitas e perfeitamente estáticas */}
      <Skybox />

      {/* Ruas, Passeios, Linhas e Candeeiros 100% Enquadrados */}
      <StreetLayout offsetX={offsetX} offsetZ={offsetZ} gridWidth={gridWidth} gridDepth={gridDepth} />

      {/* Edifícios Host (Hosts Ativos) */}
      {processedHosts.map((host: any) => (
        <Building
          key={`${scanData.id}-${host.ip}`}
          label={host.ip}
          x={(host.gridX + offsetX) * SCALE}
          z={(host.gridZ + offsetZ) * SCALE}
          portCount={host.portCount}
          riskBand={host.riskBand as any}
          isRuin={false}
          hostData={host}
          isSelected={selectedHost?.ip === host.ip}
          onClick={() => onSelectHost(host)}
          onClose={() => onSelectHost(null)}
          onOpenDetails={() => onOpenDetails(host)}
        />
      ))}

      {/* Relíquias (Ruins) */}
      {processedRuins.map((ruin: any) => (
        <Building
          key={`${scanData.id}-ruin-${ruin.ip}`}
          label={ruin.ip}
          x={(ruin.gridX + offsetX) * SCALE}
          z={(ruin.gridZ + offsetZ) * SCALE}
          portCount={2} 
          riskBand={ruin.riskBand as any}
          isRuin={true}
          onClick={() => onSelectHost(ruin)}
          hostData={ruin}
          isSelected={selectedHost?.ip === ruin.ip}
          onClose={() => onSelectHost(null)}
          onOpenDetails={() => onOpenDetails(ruin)}
        />
      ))}

      {/* Efeitos Especiais: O Bloom é vital para o estilo Cyberpunk Neon */}
      {/* LuminanceThreshold alto para afetar APENAS cores puras e preservar a escuridão geral (não fica "baço") */}
      <EffectComposer>
        <Bloom luminanceThreshold={0.2} luminanceSmoothing={0.9} intensity={2.0} />
      </EffectComposer>
    </>
  );
}

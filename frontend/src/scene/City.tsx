import { EffectComposer, Bloom } from '@react-three/postprocessing';
import { Stars } from '@react-three/drei';
import * as THREE from 'three';
import { useMemo } from 'react';
import { Building, BAND_COLORS } from './Building';
import { buildCityGrid, type PlacedDistrict } from './cityGrid';
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
      <mesh renderOrder={-1}>
        <planeGeometry args={[120, 120]} />
        {/* toneMapped={false} garante que brilha imenso e não fica cinzento */}
        <meshBasicMaterial 
          map={moonTexture} 
          transparent={true} 
          toneMapped={false} 
          color="#ffffff" 
          depthWrite={false}
          depthTest={false}
        />
      </mesh>
    </group>
  );
}


/**
 * A placa de chao de um bairro. As dimensoes vem do backend (District) em vez de
 * serem reconstituidas a partir das posicoes dos hosts -- e para isso que o record
 * viaja no JSON.
 */
function DistrictPlate({ district, offsetX, offsetZ }:
  { district: PlacedDistrict, offsetX: number, offsetZ: number }) {

  // O centro da placa e o centro das celulas que ela cobre, nao o canto.
  const centerX = (district.startX + (district.columns - 1) / 2 + offsetX) * SCALE;
  const centerZ = ((district.rows - 1) / 2 + offsetZ) * SCALE;
  const width = district.columns * SCALE;
  const depth = district.rows * SCALE;
  const color = BAND_COLORS[district.band as keyof typeof BAND_COLORS] || BAND_COLORS.UNKNOWN;

  return (
    <group position={[centerX, 0, centerZ]}>
      <mesh position={[0, 0.02, 0]} rotation={[-Math.PI / 2, 0, 0]}>
        <planeGeometry args={[width, depth]} />
        <meshBasicMaterial color={color} transparent opacity={0.06} depthWrite={false} />
      </mesh>
      <lineSegments position={[0, 0.04, 0]} rotation={[-Math.PI / 2, 0, 0]}>
        <edgesGeometry args={[new THREE.PlaneGeometry(width, depth)]} />
        <lineBasicMaterial color={color} transparent opacity={0.5} />
      </lineSegments>
    </group>
  );
}

export function City({ scanData, selectedHost, onSelectHost, onOpenDetails }: CityProps) {
  // Toda a traducao do layout do backend para a grelha vive no cityGrid, fora do
  // React, para poder ser testada. Ver o aviso la sobre nao recompactar aqui.
  const { hosts: processedHosts, ruins: processedRuins, districts,
          layoutW, layoutD, offsetX, offsetZ } = useMemo(
    () => buildCityGrid(scanData), [scanData]);

  // Forcamos o asfalto (chao) a ter pelo menos 16x16 quarteiroes
  // Mas ja nao usamos isto para mover os edificios!
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

      {/* Placas de chao dos bairros: e o que torna o zonamento por risco legivel
          de relance, sem ser preciso ler a cor de cada edificio um a um. */}
      {districts.map((district) => (
        <DistrictPlate
          key={`${scanData.id}-district-${district.band}`}
          district={district}
          offsetX={offsetX}
          offsetZ={offsetZ}
        />
      ))}

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
          isNew={host.isNew}
          isChanged={host.isChanged}
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
          portCount={ruin.portCount || 2} 
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

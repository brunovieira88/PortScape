import { EffectComposer, Bloom } from '@react-three/postprocessing';
import { Stars, Sparkles } from '@react-three/drei';
import * as THREE from 'three';
import { useMemo } from 'react';
import { Building } from './Building';
import { StreetControls } from './StreetControls';
import { StreetLayout } from './StreetLayout';



// SCALE a 13 para reduzir a distância entre casas/prédios (sem mexer no tamanho deles)
const SCALE = 13; 

interface CityProps {
  scanData: any;
  selectedHost: any;
  onSelectHost: (host: any) => void;
  onOpenDetails: (host: any) => void;
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
  
  // Tamanho real dos edifícios calculados pelo backend
  const layoutW = scanData.layout.width / backendSpacing;
  const layoutD = scanData.layout.depth / backendSpacing;
  
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

      {/* Céu Cyberpunk: Estrelas dentro do alcance da câmara (Z < 1000) */}
      <Stars radius={300} depth={150} count={8000} factor={6} saturation={1} fade speed={1} />
      
      <CrescentMoon />

      {/* Ruas, Passeios, Linhas e Candeeiros 100% Enquadrados */}
      <StreetLayout scanData={scanData} offsetX={offsetX} offsetZ={offsetZ} gridWidth={gridWidth} gridDepth={gridDepth} />

      {/* Renderização dos Edifícios "Vivos" */}
      {scanData.hosts.map((host: any) => (
        <Building
          key={`${scanData.id}-${host.ip}`}
          label={host.ip}
          x={((host.position.x / backendSpacing) + offsetX) * SCALE}
          z={((host.position.z / backendSpacing) + offsetZ) * SCALE}
          portCount={host.portCount}
          riskBand={host.riskBand as any}
          isRuin={false}
          onClick={() => onSelectHost(host)}
          hostData={host}
          isSelected={selectedHost?.ip === host.ip}
          onClose={() => onSelectHost(null)}
          onOpenDetails={() => onOpenDetails(host)}
        />
      ))}

      {/* Renderização das Ruínas (Hosts que desapareceram) */}
      {scanData.ruins.map((ruin: any) => (
        <Building
          key={`${scanData.id}-ruin-${ruin.ip}`}
          label={ruin.ip}
          x={((ruin.position.x / backendSpacing) + offsetX) * SCALE}
          z={((ruin.position.z / backendSpacing) + offsetZ) * SCALE}
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

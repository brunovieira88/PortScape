import { Stars } from '@react-three/drei';
import type { Host, Scan } from '../api/types';
import * as THREE from 'three';
import { useMemo } from 'react';
import { Building } from './Building';
import { buildCityGrid, BLOCK_SCALE as SCALE, RUIN_MIN_PORTS } from './cityGrid';
import { StreetControls } from './StreetControls';
import { StreetLayout } from './StreetLayout';





import { useFrame } from '@react-three/fiber';
import { useRef } from 'react';

interface CityProps {
  scanData: Scan;
  selectedHost: Host | null;
  onSelectHost: (host: Host | null) => void;
  onOpenDetails: (host: Host) => void;
  teleportTarget?: { ip: string, nonce: number } | null;
}

/** Azul-noite quase preto. E o horizonte e o nevoeiro ao mesmo tempo. */
const HORIZON = '#070a14';


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
          fog={false}
          color="#ffffff"
          depthWrite={false}
        />
      </mesh>
    </group>
  );
}


export function City({ scanData, selectedHost, onSelectHost, onOpenDetails, teleportTarget }: CityProps) {
  // Toda a traducao do layout do backend para a grelha vive no cityGrid, fora do
  // React, para poder ser testada. Ver o aviso la sobre nao recompactar aqui.
  // O chao (groundW/groundD) e maior do que a cidade de proposito, para uma rede
  // pequena nao ficar sobre um selo. Vem do cityGrid, que e tambem quem limita para
  // onde a camara pode andar -- se fossem calculados a parte, voltavam a divergir.
  const { hosts: processedHosts, ruins: processedRuins,
          offsetX, offsetZ, groundW: gridWidth, groundD: gridDepth } = useMemo(
    () => buildCityGrid(scanData), [scanData]);

  return (
    <>
      <StreetControls scanData={scanData} teleportTarget={teleportTarget} />

      {/* A cor do horizonte e o nevoeiro sao a mesma: e isso que faz a cidade
          desvanecer-se ao longe em vez de terminar num corte seco.

          Sem nevoeiro, um edificio a 500 unidades e exatamente tao vivo como um a 20 e
          a cena fica plana -- nao ha nenhuma outra pista de profundidade, porque nao ha
          um unico material com sombreado em toda a cidade. */}
      <color attach="background" args={[HORIZON]} />
      <fog attach="fog" args={[HORIZON, 90, 620]} />

      {/* Luz do ceu: azul por cima, quase nada por baixo. Da a queda natural de cima
          para baixo sem precisar de candeeiros. E deliberadamente mais clara do que o
          HORIZON -- a cor do nevoeiro serve para o ar, nao para encher as sombras, e
          com ela as faces viradas ao contrario da lua ficavam pretas puras. */}
      <hemisphereLight args={['#2a3558', '#05060a', 1.2]} />

      {/* A lua e a unica fonte direccional, e vem do sitio onde ela esta desenhada --
          se viesse de outro lado, as faces iluminadas contradiziam o ceu.

          Sem castShadow de proposito: a passagem de sombra re-renderiza todos os
          projectores uma vez por frame, e num /24 sao umas 700 caixas. O que da forma
          aos edificios e a luz direccional em si, nao a sombra projectada -- esta
          custava a parte mais cara para o ganho mais pequeno numa cena quase preta. */}
      <directionalLight position={[300, 200, -800]} intensity={1.1} color="#9fb4ff" />

      {/* Céu Cyberpunk: Estrelas e Lua infinitas e perfeitamente estáticas */}
      <Skybox />

      {/* Ruas, Passeios, Linhas e Candeeiros 100% Enquadrados */}
      <StreetLayout offsetX={offsetX} offsetZ={offsetZ} gridWidth={gridWidth} gridDepth={gridDepth} />

      {/* Os bairros nao levam nenhuma marca no chao. Ja foram placas translucidas e
          depois faixas na fronteira, e ambas liam como overlay de interface caido
          dentro da cena. O zonamento continua legivel sem isso: cada edificio ja tem a
          cor da sua faixa e os bairros ja estao fisicamente separados pelo intervalo
          entre eles. O District continua a viajar no JSON para quem o queira usar. */}

      {/* Edifícios Host (Hosts Ativos) */}
      {processedHosts.map(host => (
        <Building
          key={`${scanData.id}-${host.ip}`}
          label={host.ip}
          x={(host.gridX + offsetX) * SCALE}
          z={(host.gridZ + offsetZ) * SCALE}
          portCount={host.portCount}
          riskBand={host.riskBand ?? 'UNKNOWN'}
          vendor={host.vendor}
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
      {processedRuins.map(ruin => (
        <Building
          key={`${scanData.id}-ruin-${ruin.ip}`}
          label={ruin.ip}
          x={(ruin.gridX + offsetX) * SCALE}
          z={(ruin.gridZ + offsetZ) * SCALE}
          portCount={ruin.portCount || RUIN_MIN_PORTS}
          riskBand={ruin.riskBand ?? 'UNKNOWN'}
          vendor={ruin.vendor}
          isRuin={true}
          onClick={() => onSelectHost(ruin)}
          hostData={ruin}
          isSelected={selectedHost?.ip === ruin.ip}
          onClose={() => onSelectHost(null)}
          onOpenDetails={() => onOpenDetails(ruin)}
        />
      ))}

      {/* O EffectComposer/Bloom dava o brilho neon, mas era -- medido -- o maior custo
          de frame de toda a cena, mais do que todos os edificios juntos. Removido por
          desempenho; os materiais toneMapped={false} continuam a dar-lhes um brilho
          proprio sem precisar de um pass de pos-processamento inteiro. */}
    </>
  );
}

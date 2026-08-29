import { Edges } from '@react-three/drei';
import { useFrame } from '@react-three/fiber';
import { useRef } from 'react';
import * as THREE from 'three';

// Drone Caçador / Nave Furtiva (Sci-Fi) 
function HunterDrone({ color, altitude, radius }: { color: string, altitude: number, radius: number }) {
  const groupRef = useRef<THREE.Group>(null);
  
  useFrame((state) => {
    if (groupRef.current) {
      // Movimento orbital (roda no eixo Y)
      groupRef.current.rotation.y = state.clock.elapsedTime * 0.5;
      // Flutuação suave
      groupRef.current.position.y = altitude + Math.sin(state.clock.elapsedTime * 2) * 1.0;
    }
  });

  return (
    <group ref={groupRef} position={[0, altitude, 0]}>
      {/* Corpo principal posicionado na extremidade do raio */}
      <group position={[radius, 0, 0]}>
        
        {/* Chassis Furtivo (Pirâmide achatada a apontar para a frente, ou seja, -Z) */}
        <mesh rotation={[-Math.PI / 2, 0, 0]}>
          <cylinderGeometry args={[0, 1.5, 5, 3]} />
          <meshBasicMaterial color={color} wireframe={true} />
        </mesh>
        
        {/* Asas Delta Laterais */}
        <mesh position={[0, 0.1, 1]} rotation={[0, 0, Math.PI / 2]}>
          <cylinderGeometry args={[0, 0.5, 6, 3]} />
          <meshBasicMaterial color={color} wireframe={true} />
        </mesh>

        {/* Propulsores de Iões Traseiros (Glow Ciano) */}
        <mesh position={[-0.8, 0.2, 2.2]} rotation={[Math.PI / 2, 0, 0]}>
          <cylinderGeometry args={[0.2, 0.3, 1, 8]} />
          <meshBasicMaterial color="#00f0ff" toneMapped={false} />
        </mesh>
        <mesh position={[0.8, 0.2, 2.2]} rotation={[Math.PI / 2, 0, 0]}>
          <cylinderGeometry args={[0.2, 0.3, 1, 8]} />
          <meshBasicMaterial color="#00f0ff" toneMapped={false} />
        </mesh>
        
      </group>
    </group>
  );
}

interface Props {
  portCount: number;
  color: string;
  isRuin: boolean;
  riskBand: string;
}

export function Architecture({ portCount, color, isRuin, riskBand }: Props) {
  // Se for ruína, usamos uma cor mais visível e opacidade média para parecer um "fantasma"
  // mas sem ficar invisível no ecrã preto!
  const activeColor = isRuin ? '#666666' : color;
  const W = 10;
  const D = 10;
  const elements = [];

  const lineProps = {
    color: activeColor,
    transparent: true,
    opacity: isRuin ? 0.6 : 1.0,
    toneMapped: false,
  };

  // ==========================================
  // MODELO 1: CASA DE SUBÚRBIO DETALHADA (portCount 1-3)
  // ==========================================
  if (portCount <= 3) {
    const H = 6;
    
    // Esqueleto Base da Casa (Paredes)
    elements.push(
      <mesh key="house-base" position={[0, H / 2, 0]}>
        <boxGeometry args={[W, H, D]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.3 : 0.6} />
      </mesh>
    );

    // Porta da Frente Central
    elements.push(
      <mesh key="house-door" position={[0, 1.5, D / 2 + 0.05]}>
        <planeGeometry args={[2, 3]} />
        <meshBasicMaterial color={activeColor} wireframe={true} />
      </mesh>,
      // Maçaneta da porta
      <mesh key="house-doorknob" position={[0.7, 1.5, D / 2 + 0.1]}>
        <circleGeometry args={[0.1, 8]} />
        <meshBasicMaterial color={activeColor} />
      </mesh>
    );

    // Janelas da Frente (Esquerda e Direita)
    elements.push(
      <mesh key="house-win-l" position={[-2.5, 3, D / 2 + 0.05]}>
        <planeGeometry args={[2, 2, 2, 2]} /> {/* 2x2 grid = cruzeta da janela */}
        <meshBasicMaterial color={activeColor} wireframe={true} />
      </mesh>,
      <mesh key="house-win-r" position={[2.5, 3, D / 2 + 0.05]}>
        <planeGeometry args={[2, 2, 2, 2]} />
        <meshBasicMaterial color={activeColor} wireframe={true} />
      </mesh>
    );

    // Telhado de Duas Águas (Pitched Roof)
    const roofH = 3;
    elements.push(
      <mesh key="house-roof" position={[0, H + roofH / 2, 0]} rotation={[0, Math.PI / 4, 0]}>
        <coneGeometry args={[W * 0.75, roofH, 4, 1]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.4 : 0.8} />
      </mesh>
    );

    // Chaminé Clássica
    elements.push(
      <mesh key="house-chimney" position={[W / 4, H + roofH / 2, -D / 4]}>
        <boxGeometry args={[1, 3, 1]} />
        <meshBasicMaterial color={activeColor} wireframe={true} />
      </mesh>
    );

    return <group>{elements}</group>;
  }

  // ==========================================
  // MODELO 2: ARRANHA-CÉUS COM LOBBY (portCount > 3)
  // ==========================================
  const floorsCount = Math.min(portCount, 25);
  const floorH = 3; 
  const H = floorsCount * floorH;

  // Esqueleto Principal da Torre
  elements.push(
    <mesh key="tower-main" position={[0, H / 2, 0]}>
      <boxGeometry args={[W, H, D]} />
      <Edges {...lineProps} />
      <meshBasicMaterial visible={false} />
    </mesh>
  );

  // Geração das Fachadas de Escritórios (Janelas)
  // O wireframe de um plano divide a face em janelas quadradas
  elements.push(
    <mesh key="facade-front" position={[0, H / 2, D / 2 + 0.05]}>
      <planeGeometry args={[W, H, 4, floorsCount]} />
      <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.3 : 0.6} />
    </mesh>,
    <mesh key="facade-back" position={[0, H / 2, -D / 2 - 0.05]} rotation={[0, Math.PI, 0]}>
      <planeGeometry args={[W, H, 4, floorsCount]} />
      <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.3 : 0.6} />
    </mesh>,
    <mesh key="facade-left" position={[-W / 2 - 0.05, H / 2, 0]} rotation={[0, -Math.PI / 2, 0]}>
      <planeGeometry args={[D, H, 4, floorsCount]} />
      <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.3 : 0.6} />
    </mesh>,
    <mesh key="facade-right" position={[W / 2 + 0.05, H / 2, 0]} rotation={[0, Math.PI / 2, 0]}>
      <planeGeometry args={[D, H, 4, floorsCount]} />
      <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.3 : 0.6} />
    </mesh>
  );

  // LOBBY (Entrada Principal Térrea do Prédio)
  elements.push(
    // Cobertura da entrada
    <mesh key="lobby-roof" position={[0, 4, D / 2 + 1]}>
      <boxGeometry args={[6, 0.5, 2]} />
      <meshBasicMaterial color={activeColor} wireframe={true} />
    </mesh>,
    // Portas Duplas Gigantes de Vidro
    <mesh key="lobby-door" position={[0, 2, D / 2 + 0.1]}>
      <planeGeometry args={[4, 4, 2, 1]} />
      <meshBasicMaterial color={activeColor} wireframe={true} />
    </mesh>
  );

  // Base do Prédio (Lobby mais alto e detalhado)
  elements.push(
    <mesh key="lobby" position={[0, 3, 0]}>
      <boxGeometry args={[W + 1, 6, D + 1, 4, 1, 4]} />
      <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.4 : 0.8} />
    </mesh>
  );

  // Topo do Prédio (Roof Garden / Machinery)
  elements.push(
    <mesh key="roof-base" position={[0, H + 1.5, 0]}>
      <boxGeometry args={[W - 2, 3, D - 2, 4, 1, 4]} />
      <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.4 : 0.8} />
    </mesh>
  );

  // ==========================================
  // DECORAÇÕES ESPECÍFICAS POR NÍVEL DE RISCO
  // ==========================================
  
  // 1. SAFE / UNKNOWN / LOW: Torres de Comunicação Limpas
  if ((riskBand === 'SAFE' || riskBand === 'UNKNOWN' || riskBand === 'LOW') && portCount >= 5) {
    elements.push(
      <mesh key="comm-tower" position={[0, H + 8, 0]}>
        <cylinderGeometry args={[0.5, 0.5, 10, 8, 4]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.4 : 0.8} />
      </mesh>
    );
  }

  // 2. LOW / MEDIUM: Comercial, Néons e Heliportos
  if ((riskBand === 'LOW' || riskBand === 'MEDIUM') && portCount > 3 && !isRuin) {
    // Heliporto circular no telhado
    elements.push(
      <mesh key="helipad" position={[0, H + 3.1, 0]} rotation={[-Math.PI / 2, 0, 0]}>
        <ringGeometry args={[2, 3, 16]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={0.8} />
      </mesh>
    );
    // Pilares estruturais / Exoesqueleto nos 4 cantos do edifício para um aspeto fortificado
    const pR = W / 2 + 0.2;
    elements.push(
      <mesh key="exo-pillar-1" position={[pR, H / 2, pR]}>
        <cylinderGeometry args={[0.3, 0.3, H + 2, 4]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={0.6} />
      </mesh>,
      <mesh key="exo-pillar-2" position={[-pR, H / 2, pR]}>
        <cylinderGeometry args={[0.3, 0.3, H + 2, 4]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={0.6} />
      </mesh>,
      <mesh key="exo-pillar-3" position={[pR, H / 2, -pR]}>
        <cylinderGeometry args={[0.3, 0.3, H + 2, 4]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={0.6} />
      </mesh>,
      <mesh key="exo-pillar-4" position={[-pR, H / 2, -pR]}>
        <cylinderGeometry args={[0.3, 0.3, H + 2, 4]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={0.6} />
      </mesh>
    );
  }

  // 3. HIGH: Industrial Pesado, Refrigeração
  if (riskBand === 'HIGH' && portCount > 3) {
    // Tubagens de refrigeração duplas nas laterais
    elements.push(
      <mesh key="cooling-pipe-1" position={[W / 2 + 0.8, H / 2, -D / 4]}>
        <cylinderGeometry args={[0.8, 0.8, H, 6, 10]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.3 : 0.7} />
      </mesh>,
      <mesh key="cooling-pipe-2" position={[-W / 2 - 0.8, H / 2, -D / 4]}>
        <cylinderGeometry args={[0.8, 0.8, H, 6, 10]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.3 : 0.7} />
      </mesh>
    );
    // Anel estrutural de suporte a meia-altura
    elements.push(
      <mesh key="overhang-ring" position={[0, H * 0.5, 0]} rotation={[Math.PI / 2, 0, 0]}>
        <ringGeometry args={[W - 1, W + 2, 4]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.4 : 0.8} side={2} />
      </mesh>
    );
  }

  // 4. CRITICAL: Perigo, Opressão, Controlo Total
  if (riskBand === 'CRITICAL' && portCount > 3) {
    // Anéis Duplos Opressivos flutuando sobre a cidade
    elements.push(
      <mesh key="overhang-ring-1" position={[0, H * 0.7, 0]} rotation={[Math.PI / 2, 0, 0]}>
        <ringGeometry args={[W, W + 5, 8]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.6 : 0.9} side={2} />
      </mesh>,
      <mesh key="overhang-ring-2" position={[0, H * 0.9, 0]} rotation={[Math.PI / 2, 0, 0]}>
        <ringGeometry args={[W, W + 3, 8]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.6 : 0.9} side={2} />
      </mesh>
    );

    // Coroa / Núcleo de Dados Corrompido no topo (Para todas as torres Critical)
    elements.push(
      <mesh key="critical-core" position={[0, H + 5, 0]}>
        <icosahedronGeometry args={[3, 1]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.5 : 0.9} toneMapped={false} />
      </mesh>,
      <mesh key="critical-core-inner" position={[0, H + 5, 0]}>
        <icosahedronGeometry args={[1.5, 0]} />
        <meshBasicMaterial color={activeColor} transparent={true} opacity={isRuin ? 0.2 : 0.5} toneMapped={false} />
      </mesh>
    );

    // PATRULHA AÉREA (Nave Furtiva / Drone) apenas para as máquinas vivas
    if (!isRuin && portCount >= 8) {
      elements.push(<HunterDrone key="patrol" color={color} altitude={H + 15} radius={12} />);
    }
  }

  return <group>{elements}</group>;
}

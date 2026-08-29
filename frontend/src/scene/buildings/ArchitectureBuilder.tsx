import { Edges } from '@react-three/drei';

interface Props {
  portCount: number;
  color: string;
  isRuin: boolean;
}

export function Architecture({ portCount, color, isRuin }: Props) {
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
  // MODELO 1: CASA DE SUBÚRBIO (portCount 1-3)
  // ==========================================
  if (portCount <= 3) {
    const H = 8;
    
    // Esqueleto Base
    elements.push(
      <mesh key="house-base" position={[0, H / 2, 0]}>
        <boxGeometry args={[W, H, D]} />
        <Edges {...lineProps} />
        <meshBasicMaterial visible={false} />
      </mesh>
    );

    // Grelha de Janelas e Portas (Frente da casa Z = D/2)
    elements.push(
      <mesh key="house-front-windows" position={[0, H / 2, D / 2 + 0.1]}>
        <planeGeometry args={[W - 2, H - 2, 4, 3]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.4 : 0.8} />
      </mesh>
    );

    // Telhado detalhado (Pitched Roof) com linhas de vigas
    const roofH = 4;
    elements.push(
      <mesh key="house-roof" position={[0, H + roofH / 2, 0]} rotation={[0, Math.PI / 4, 0]}>
        <coneGeometry args={[W * 0.8, roofH, 4, 4]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.4 : 0.8} />
      </mesh>
    );

    return <group>{elements}</group>;
  }

  // ==========================================
  // MODELO 2: ARRANHA-CÉUS REALISTA (portCount > 3)
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

  // Geração da Fachada Principal (Vidros / Escritórios)
  // Usamos wireframe={true} para desenhar todas as divisórias de vidro perfeitamente
  elements.push(
    <mesh key="facade-front" position={[0, H / 2, D / 2 + 0.05]}>
      <planeGeometry args={[W, H, 6, floorsCount]} />
      <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.4 : 0.8} />
    </mesh>,
    <mesh key="facade-back" position={[0, H / 2, -D / 2 - 0.05]} rotation={[0, Math.PI, 0]}>
      <planeGeometry args={[W, H, 6, floorsCount]} />
      <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.4 : 0.8} />
    </mesh>,
    <mesh key="facade-left" position={[-W / 2 - 0.05, H / 2, 0]} rotation={[0, -Math.PI / 2, 0]}>
      <planeGeometry args={[D, H, 6, floorsCount]} />
      <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.4 : 0.8} />
    </mesh>,
    <mesh key="facade-right" position={[W / 2 + 0.05, H / 2, 0]} rotation={[0, Math.PI / 2, 0]}>
      <planeGeometry args={[D, H, 6, floorsCount]} />
      <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.4 : 0.8} />
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

  if (portCount >= 8) {
    // Torre de Comunicação Hi-Tech
    elements.push(
      <mesh key="comm-tower" position={[0, H + 8, 0]}>
        <cylinderGeometry args={[0.5, 0.5, 10, 8, 4]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.4 : 0.8} />
      </mesh>
    );
  }

  return <group>{elements}</group>;
}

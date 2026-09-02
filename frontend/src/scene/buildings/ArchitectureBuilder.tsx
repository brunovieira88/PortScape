import { Edges } from '@react-three/drei';
import type { DeviceKind } from './deviceKind';
import { FLOOR_HEIGHT, HOUSE_HEIGHT, HOUSE_ROOF_HEIGHT, MAX_FLOORS, rngFrom, towerForm,
         type Tier, type WindowStyle } from './towerForm';
import { useFrame } from '@react-three/fiber';
import { useLayoutEffect, useMemo, useRef } from 'react';
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

/**
 * Quanto o nucleo opaco encolhe face a estrutura, para nao haver z-fighting com o
 * wireframe nem com as fachadas (que ficam a 0.05 para fora).
 */
const CORE_INSET = 0.4;
/**
 * Cinzento escuro neutro da massa do edificio. Nao tinge com a cor da faixa de risco de
 * proposito: tingir igualava a massa as linhas neon e a cidade perdia o contraste
 * escuro-contra-neon que da o aspecto noturno. Continua a nao ser preto puro -- precisa
 * de sobrar alguma coisa para a luz apanhar, senao as faces de um prisma ficam todas
 * identicas e o edificio le como silhueta recortada em vez de volume.
 */
const CORE_COLOR = '#3a3d44';
/**
 * Emissivo fixo e baixo, independente da luz da cena. Sem isto a face virada para o
 * lado contrario da lua fica sem luz nenhuma e volta a ler como preta -- e era
 * exatamente essa a fachada que se via atraves das janelas (vazadas, so wireframe) da
 * casa: preto por falta de luz direta, nao pela cor do material.
 */
const CORE_EMISSIVE = '#101216';

/**
 * O material da massa dos edificios -- o unico da cena que responde a luz.
 *
 * <p>Toda a cidade era meshBasicMaterial, que ignora luz por completo: cada face tinha
 * exatamente a mesma cor, viesse a luz de onde viesse. Um standard material aqui faz as
 * faces separarem-se umas das outras e e o que da forma aos edificios. As linhas neon
 * continuam basic, porque essas sao emissivas -- nao sao iluminadas, sao a luz.
 */
function CoreMaterial() {
  return <meshStandardMaterial color={CORE_COLOR} emissive={CORE_EMISSIVE} roughness={0.85} metalness={0.15} />;
}

/**
 * O interior opaco de um edificio.
 *
 * <p>Sem isto a cidade e wireframe puro e nao oclui nada: ve-se atraves de tudo ao
 * mesmo tempo, e vinte edificios sobrepostos sao sopa de linhas. Um nucleo escuro
 * ligeiramente mais pequeno que a estrutura devolve-lhes silhueta e profundidade sem
 * tirar o neon -- as linhas passam a destacar-se contra massa escura em vez de contra
 * as linhas umas das outras.
 *
 * <p>As ruinas nao levam nucleo, de proposito: passam a ser as unicas coisas
 * atravessaveis a vista na cidade, e e isso que as faz ler como fantasmas.
 */
function SolidCore({ width, height, depth }: { width: number, height: number, depth: number }) {
  return (
    <mesh position={[0, height / 2, 0]}>
      <boxGeometry args={[width - CORE_INSET, height - CORE_INSET, depth - CORE_INSET]} />
      <CoreMaterial />
    </mesh>
  );
}

/**
 * Quanta geometria um edificio desenha, consoante a distancia a camara.
 *
 * <p>Uma torre com todos os adereços sao 25 meshes, cada um com o seu draw call. Num
 * /24 cheio isso passa dos dois mil objectos, e os adereços -- antenas, tubos, drones a
 * orbitar -- sao invisiveis a essa distancia de qualquer forma. Longe fica a silhueta,
 * que e o que se le de longe; perto fica tudo.
 */
export const DETAIL = { SILHOUETTE: 0, STRUCTURE: 1, FULL: 2 } as const;
export type DetailLevel = 0 | 1 | 2;

/**
 * Janelas acesas de uma torre.
 *
 * <p>A fachada em wireframe desenha uma grelha perfeita e regular, que le como
 * esquema tecnico. Umas quantas janelas acesas quebram essa regularidade e sao o que
 * faz uma cidade nocturna parecer habitada -- e dao escala, porque se contam andares.
 *
 * <p>Quais acendem sai do IP, nao de {@code Math.random}: o mesmo host tem sempre as
 * mesmas janelas em todos os scans, como tudo o resto neste projecto. Uma cidade que
 * pisca ao recarregar a pagina nao e uma cidade, e ruido.
 */
/**
 * Uma mancha de luz no chao, por baixo do edificio.
 *
 * <p>Sem isto os edificios parecem colados a um plano preto. O halo da-lhes assento: a
 * luz que eles proprios emitem tem de cair nalgum lado, e e o que faz a diferenca entre
 * um objecto pousado num sitio e um objecto a flutuar.
 *
 * <p>Justo a pegada de proposito. Aberto de mais deixa de ser luz derramada e passa a
 * ser o chao pintado da cor do predio, que e outra coisa e nao fica bem.
 */
const GLOW_TEXTURE = (() => {
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 128;
  const ctx = canvas.getContext('2d');
  if (ctx) {
    const g = ctx.createRadialGradient(64, 64, 0, 64, 64, 64);
    g.addColorStop(0, 'rgba(255,255,255,0.6)');
    g.addColorStop(0.35, 'rgba(255,255,255,0.16)');
    g.addColorStop(1, 'rgba(255,255,255,0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, 128, 128);
  }
  return new THREE.CanvasTexture(canvas);
})();

function GroundGlow({ color, radius }: { color: string, radius: number }) {
  return (
    <mesh position={[0, 0.06, 0]} rotation={[-Math.PI / 2, 0, 0]}>
      <planeGeometry args={[radius * 2, radius * 2]} />
      <meshBasicMaterial map={GLOW_TEXTURE} color={color} transparent
                         blending={THREE.AdditiveBlending} depthWrite={false} toneMapped={false} />
    </mesh>
  );
}

/**
 * Baliza de topo, como as luzes de obstaculo de um predio alto.
 *
 * <p>E quase a unica coisa que se mexe na cidade quando se esta parado. Um plano
 * completamente estatico le como maquete; um pisca lento le como sitio habitado.
 */
function Beacon({ y, color, phase }: { y: number, color: string, phase: number }) {
  const ref = useRef<THREE.Mesh>(null);
  useFrame((state) => {
    if (!ref.current) { return; }
    const pulse = Math.sin(state.clock.elapsedTime * 1.6 + phase);
    const material = ref.current.material as THREE.MeshBasicMaterial;
    material.opacity = pulse > 0.6 ? 0.35 + (pulse - 0.6) * 1.6 : 0.05;
  });
  return (
    <mesh ref={ref} position={[0, y, 0]}>
      <sphereGeometry args={[0.45, 8, 8]} />
      <meshBasicMaterial color={color} transparent opacity={0.1} toneMapped={false} fog={false} />
    </mesh>
  );
}

/**
 * A fachada de um patamar.
 *
 * <p>As janelas sao instanciadas -- uma so chamada de desenho por patamar, seja qual
 * for o numero delas -- e o brilho de cada uma vai na cor da instancia, o que da a
 * irregularidade de "umas acesas, outras nao" sem custar nada.
 *
 * <p>Duas linguagens: <b>faixas</b> horizontais correndo o andar quase todo, que e o
 * vocabulario da laje moderna, e <b>frisos</b> verticais estreitos e altos, que e o do
 * arranha-ceus. Quadrados a meio da fachada nao sao nem uma coisa nem outra.
 */
interface WindowSlot {
  pos: [number, number, number]; rotY: number; scaleX: number; scaleY: number;
}

/**
 * Um bloco de janelas com a mesma cor. Duas instancias deste (acesas / apagadas) sao
 * mais simples e mais fiaveis do que dar cor por instancia dentro de um so
 * InstancedMesh -- a tentativa de tingir por instancia (setColorAt +
 * material.vertexColors) nunca chegou a pintar de forma consistente entre motores, e
 * uma cor fixa por malha e o mesmo truque que ja funciona no resto da cena.
 */
function InstancedWindows({ slots, color }: { slots: WindowSlot[], color: string }) {
  const ref = useRef<THREE.InstancedMesh>(null);

  useLayoutEffect(() => {
    const mesh = ref.current;
    if (!mesh) { return; }
    const dummy = new THREE.Object3D();
    slots.forEach((slot, i) => {
      dummy.position.set(...slot.pos);
      dummy.rotation.set(0, slot.rotY, 0);
      // A geometria e um quadrado de 1x1; cada instancia estica-se para a sua face.
      dummy.scale.set(slot.scaleX, slot.scaleY, 1);
      dummy.updateMatrix();
      mesh.setMatrixAt(i, dummy.matrix);
    });
    mesh.instanceMatrix.needsUpdate = true;
  }, [slots]);

  if (slots.length === 0) { return null; }

  return (
    <instancedMesh ref={ref} args={[undefined, undefined, slots.length]}>
      <planeGeometry args={[1, 1]} />
      <meshBasicMaterial color={color} toneMapped={false} transparent opacity={0.95} />
    </instancedMesh>
  );
}

function TierWindows({ tier, style, color, floorHeight, seed, litCount }:
  { tier: Tier, style: WindowStyle, color: string, floorHeight: number, seed: number, litCount: number }) {

  const floors = Math.max(1, Math.round(tier.height / floorHeight));
  // Altura real de um andar *neste* patamar. Um SETBACK ou uma agulha tem patamares
  // muito mais baixos que FLOOR_HEIGHT -- dimensionar a janela por essa constante fixa
  // fazia-a sair maior do que o proprio patamar, e ela saltava por cima e por baixo da
  // fachada. Aqui a janela nunca e maior do que o andar onde assenta.
  const sliceHeight = tier.height / floors;

  const instances = useMemo(() => {
    const rng = rngFrom(seed);
    const out: {
      pos: [number, number, number], rotY: number,
      scaleX: number, scaleY: number,
    }[] = [];

    const faces: { rotY: number, along: 'x' | 'z', sign: number }[] = [
      { rotY: 0, along: 'x', sign: 1 },
      { rotY: Math.PI, along: 'x', sign: -1 },
      { rotY: Math.PI / 2, along: 'z', sign: 1 },
      { rotY: -Math.PI / 2, along: 'z', sign: -1 },
    ];

    for (const face of faces) {
      const span = face.along === 'x' ? tier.width : tier.depth;
      const outward = (face.along === 'x' ? tier.depth : tier.width) / 2 + 0.05;
      const cols = style === 'RIBBON' ? 1 : Math.max(2, Math.round(span / 1.6));
      // O tamanho sai da face onde a janela vai assentar, nao da largura do edificio:
      // numa laje as faces estreitas tem menos de metade da largura das outras, e uma
      // faixa dimensionada pela face larga saia para fora do predio pelos dois lados.
      const scaleX = style === 'RIBBON'
        ? span * 0.82
        : Math.min(0.55, (span / cols) * 0.34);
      const scaleY = (style === 'RIBBON' ? sliceHeight * 0.26 : sliceHeight * 0.62);
      for (let floor = 0; floor < floors; floor++) {
        for (let col = 0; col < cols; col++) {
          // As faixas desenham-se sempre; os frisos sao esparsos de proposito.
          if (style === 'STRIP' && rng() > 0.82) { continue; }
          const local = cols === 1 ? 0 : (col - (cols - 1) / 2) * (span / cols);
          const y = tier.base + floor * sliceHeight + sliceHeight / 2;
          out.push({
            pos: face.along === 'x'
              ? [local, y, face.sign * outward]
              : [face.sign * outward, y, local],
            rotY: face.rotY,
            scaleX,
            scaleY,
          });
        }
      }
    }
    return out;
  }, [tier, style, sliceHeight, seed, floors]);

  // Quais acendem sai do numero de portas abertas do host, nao de sorte: e a mesma
  // ideia que ja da a altura do edificio, aplicada a fachada. Um shuffle com semente
  // propria (distinta da que desenha a grelha) escolhe sempre as mesmas janelas para o
  // mesmo host, em qualquer scan.
  const { lit, dim } = useMemo(() => {
    const indices = instances.map((_, i) => i);
    const rng = rngFrom(seed + 104729);
    for (let i = indices.length - 1; i > 0; i--) {
      const j = Math.floor(rng() * (i + 1));
      [indices[i], indices[j]] = [indices[j], indices[i]];
    }
    const litSet = new Set(indices.slice(0, Math.min(litCount, indices.length)));
    const lit: WindowSlot[] = [];
    const dim: WindowSlot[] = [];
    instances.forEach((slot, i) => (litSet.has(i) ? lit : dim).push(slot));
    return { lit, dim };
  }, [instances, litCount, seed]);

  const dimColor = useMemo(() => `#${new THREE.Color(color).multiplyScalar(0.18).getHexString()}`, [color]);

  if (instances.length === 0) { return null; }

  return (
    <>
      <InstancedWindows slots={lit} color={color} />
      <InstancedWindows slots={dim} color={dimColor} />
    </>
  );
}

interface Props {
  portCount: number;
  color: string;
  isRuin: boolean;
  riskBand: string;
  /** O tipo de maquina, do fabricante do MAC. Escolhe a forma, nunca a altura. */
  kind?: DeviceKind;
  detail?: DetailLevel;
  /** Semente estavel do host (o IP), para a variacao nao mudar entre scans. */
  seed?: number;
}

export function Architecture({ portCount, color, isRuin, riskBand, kind = 'GENERIC', detail = DETAIL.FULL, seed = 0 }: Props) {
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
  // A vivenda e para maquinas pequenas que nao sabemos o que sao. Uma coisa que
  // sabemos ser um router ou um sensor tem forma propria por pouca porta que tenha --
  // cai no modelo da torre, que a esta altura da uma capsula ou um mastro.
  if (portCount <= 3 && kind === 'GENERIC') {
    const H = HOUSE_HEIGHT;
    
    if (!isRuin) {
      elements.push(<SolidCore key="house-core" width={W} height={H} depth={D} />);
    }

    // Esqueleto Base da Casa (Paredes)
    elements.push(
      <mesh key="house-base" position={[0, H / 2, 0]}>
        <boxGeometry args={[W, H, D]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.3 : 0.6} />
      </mesh>
    );

    // Porta da Frente Central
    if (detail >= DETAIL.STRUCTURE) {
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

    }

    if (!isRuin && detail >= DETAIL.STRUCTURE) {
      elements.push(<GroundGlow key="glow" color={activeColor} radius={W * 0.7} />);
    }

    // Telhado de Duas Águas (Pitched Roof)
    const roofH = HOUSE_ROOF_HEIGHT;
    elements.push(
      <mesh key="house-roof" position={[0, H + roofH / 2, 0]} rotation={[0, Math.PI / 4, 0]}>
        <coneGeometry args={[W * 0.75, roofH, 4, 1]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.4 : 0.8} />
      </mesh>
    );

    if (!isRuin) {
      elements.push(
        <mesh key="house-roof-core" position={[0, H + roofH / 2, 0]} rotation={[0, Math.PI / 4, 0]}>
          <coneGeometry args={[W * 0.75 - CORE_INSET, roofH - CORE_INSET, 4, 1]} />
          <CoreMaterial />
        </mesh>
      );
    }

    // Chaminé Clássica
    if (detail >= DETAIL.STRUCTURE) {
    elements.push(
      <mesh key="house-chimney" position={[W / 4, H + roofH / 2, -D / 4]}>
        <boxGeometry args={[1, 3, 1]} />
        <meshBasicMaterial color={activeColor} wireframe={true} />
      </mesh>
    );

    }

    return <group>{elements}</group>;
  }

  // ==========================================
  // MODELO 2: TORRE (portCount > 3)
  // ==========================================
  // Pelo menos um andar. Um host pode responder ao ping sem ter uma unica porta
  // aberta, e antes do tipo de dispositivo esse caso caia sempre na vivenda; agora um
  // sensor sem portas vem parar aqui e um edificio de altura zero nao e um edificio.
  const floorsCount = Math.max(1, Math.min(portCount, MAX_FLOORS));
  const floorH = FLOOR_HEIGHT;
  const H = floorsCount * floorH;
  const form = towerForm(seed, floorsCount, floorH, kind);
  const base = form.tiers[0];
  const towerW = base.width;
  const towerD = base.depth;

  form.tiers.forEach((tier, i) => {
    const cy = tier.base + tier.height / 2;

    if (!isRuin) {
      elements.push(
        <mesh key={`tier-core-${i}`} position={[0, cy, 0]} rotation={[0, form.rotation, 0]}>
          <boxGeometry args={[tier.width - CORE_INSET, tier.height, tier.depth - CORE_INSET]} />
          <CoreMaterial />
        </mesh>
      );
    }

    elements.push(
      <mesh key={`tier-${i}`} position={[0, cy, 0]} rotation={[0, form.rotation, 0]}>
        <boxGeometry args={[tier.width, tier.height, tier.depth]} />
        <Edges {...lineProps} />
        <meshBasicMaterial visible={false} />
      </mesh>
    );

    if (detail >= DETAIL.STRUCTURE) {
      elements.push(
        <group key={`facade-${i}`} rotation={[0, form.rotation, 0]}>
          <TierWindows tier={tier} style={form.windows} color={activeColor}
                       floorHeight={floorH} seed={seed + i * 7919}
                       litCount={isRuin ? 0 : portCount} />
        </group>
      );
    }
  });

  // Coroamento: o que remata a silhueta e distingue um arquetipo do outro.
  const top = form.tiers[form.tiers.length - 1];
  const crownY = top.base + top.height;
  if (form.style === 'SPIRE') {
    elements.push(
      <mesh key="crown-spire" position={[0, crownY + form.crown / 2, 0]}>
        <coneGeometry args={[top.width * 0.18, form.crown, 4, 1]} />
        <meshBasicMaterial color={activeColor} wireframe transparent opacity={isRuin ? 0.4 : 0.9} toneMapped={false} />
      </mesh>
    );
  } else {
    if (!isRuin) {
      elements.push(
        <mesh key="crown-core" position={[0, crownY + form.crown / 2, 0]} rotation={[0, form.rotation, 0]}>
          <boxGeometry args={[top.width * 0.6 - 0.2, form.crown, top.depth * 0.6 - 0.2]} />
          <CoreMaterial />
        </mesh>
      );
    }
    elements.push(
      <mesh key="crown" position={[0, crownY + form.crown / 2, 0]} rotation={[0, form.rotation, 0]}>
        <boxGeometry args={[top.width * 0.6, form.crown, top.depth * 0.6]} />
        <Edges {...lineProps} />
        <meshBasicMaterial visible={false} />
      </mesh>
    );
  }

  if (!isRuin && floorsCount >= 7) {
    elements.push(
      <Beacon key="beacon" y={crownY + form.crown + 0.8} color={activeColor} phase={(seed % 100) / 16} />
    );
  }

  if (!isRuin && detail >= DETAIL.STRUCTURE) {
    elements.push(<GroundGlow key="glow" color={activeColor} radius={Math.max(towerW, towerD) * 0.85} />);
  }

  // Lobby: a base tem sempre po de rua, seja qual for a forma.
  if (detail >= DETAIL.STRUCTURE) {
    elements.push(
      <mesh key="lobby" position={[0, 1.6, 0]} rotation={[0, form.rotation, 0]}>
        <boxGeometry args={[base.width + 1.2, 3.2, base.depth + 1.2]} />
        <Edges {...lineProps} />
        <meshBasicMaterial visible={false} />
      </mesh>
    );
  }

  // ==========================================
  // DECORAÇÕES ESPECÍFICAS POR NÍVEL DE RISCO
  // ==========================================
  if (detail < DETAIL.FULL) {
    return <group>{elements}</group>;
  }

  
  // 1. SAFE / UNKNOWN / LOW: Torres de Comunicação Limpas
  if ((riskBand === 'UNKNOWN' || riskBand === 'LOW') && portCount >= 5) {
    elements.push(
      <mesh key="comm-tower" position={[0, H + 8, 0]}>
        <cylinderGeometry args={[0.5, 0.5, 10, 8, 4]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.4 : 0.8} />
      </mesh>
    );
  }

  // 2. LOtowerW / MEDIUM: Comercial, Néons e Heliportos
  if ((riskBand === 'LOW' || riskBand === 'MEDIUM') && portCount > 3 && !isRuin) {
    // Pilares estruturais / Exoesqueleto nos 4 cantos do edifício para um aspeto fortificado
    const pR = towerW / 2 + 0.2;
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
      <mesh key="cooling-pipe-1" position={[towerW / 2 + 0.8, H / 2, -towerD / 4]}>
        <cylinderGeometry args={[0.8, 0.8, H, 6, 10]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.3 : 0.7} />
      </mesh>,
      <mesh key="cooling-pipe-2" position={[-towerW / 2 - 0.8, H / 2, -towerD / 4]}>
        <cylinderGeometry args={[0.8, 0.8, H, 6, 10]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.3 : 0.7} />
      </mesh>
    );
  }

  // 4. CRITICAL: Perigo, Opressão, Controlo Total
  if (riskBand === 'CRITICAL' && portCount > 3) {
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

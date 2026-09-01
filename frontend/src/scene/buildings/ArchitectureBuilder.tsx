import { Edges } from '@react-three/drei';
import type { DeviceKind } from './deviceKind';
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

/** Andares de uma torre, e altura de cada um. */
const FLOOR_HEIGHT = 3;
const MAX_FLOORS = 25;
/** Paredes e telhado de uma casa (portCount <= 3). */
const HOUSE_HEIGHT = 6;
const HOUSE_ROOF_HEIGHT = 3;

/**
 * Altura da estrutura, do chao ao topo do telhado. E daqui que sai a posicao da
 * etiqueta por cima do edificio -- calcula-la a parte fazia-a flutuar cinco unidades
 * acima do telhado das casas e ficar por baixo do das torres.
 *
 * <p>Nao inclui os adereços condicionais (antena, drone), que sao decoracao e podem
 * passar acima disto de proposito.
 */
export function buildingHeight(portCount: number, seed = 0, kind: DeviceKind = 'GENERIC'): number {
  if (portCount <= 3 && kind === 'GENERIC') {
    return HOUSE_HEIGHT + HOUSE_ROOF_HEIGHT;
  }
  const floors = Math.max(1, Math.min(portCount, MAX_FLOORS));
  // O coroamento varia com o arquetipo -- uma agulha remata muito acima de uma laje --
  // por isso a altura tem de sair da mesma forma que desenha o edificio.
  return floors * FLOOR_HEIGHT + towerForm(seed, floors, FLOOR_HEIGHT, kind).crown;
}

/**
 * Quanto o nucleo opaco encolhe face a estrutura, para nao haver z-fighting com o
 * wireframe nem com as fachadas (que ficam a 0.05 para fora).
 */
const CORE_INSET = 0.4;
/**
 * A massa do edificio. Escuro, mas nao preto: tem de sobrar alguma coisa para a luz
 * apanhar, senao as tres faces visiveis de um prisma ficam identicas e o edificio le
 * como silhueta recortada em vez de volume. Fica abaixo do limiar do bloom de proposito.
 */
const CORE_COLOR = '#0d1018';

/**
 * O material da massa dos edificios -- o unico da cena que responde a luz.
 *
 * <p>Toda a cidade era meshBasicMaterial, que ignora luz por completo: cada face tinha
 * exatamente a mesma cor, viesse a luz de onde viesse. Um standard material aqui faz as
 * faces separarem-se umas das outras e e o que da forma aos edificios. As linhas neon
 * continuam basic, porque essas sao emissivas -- nao sao iluminadas, sao a luz.
 */
function CoreMaterial() {
  return <meshStandardMaterial color={CORE_COLOR} roughness={0.85} metalness={0.15} />;
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

/** Gerador congruencial: deterministico, e chega perfeitamente para variacao visual. */
function rngFrom(seed: number) {
  let state = (seed || 1) & 0x7fffffff;
  return () => (state = (state * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;
}

type TowerStyle = 'SLAB' | 'PRISM' | 'SETBACK' | 'SPIRE';
type WindowStyle = 'RIBBON' | 'STRIP';

interface Tier { base: number; width: number; depth: number; height: number; }

interface TowerForm {
  style: TowerStyle;
  windows: WindowStyle;
  tiers: Tier[];
  /** Rotacao do edificio inteiro, para a cidade nao ficar toda alinhada a esquadro. */
  rotation: number;
  crown: number;
}

/**
 * A forma de uma torre, a partir do IP.
 *
 * <p>Sem isto todas as torres sao a mesma caixa de 10x10 e a cidade le como um asset
 * repetido. Quatro arquetipos com variacao continua dentro de cada um dao skyline:
 * lajes largas e baixas, prismas, torres com recuos, e agulhas finas com antena.
 *
 * <p>A altura total continua a ser {@code andares x FLOOR_HEIGHT} -- e o numero de
 * portas que a manda, e isso e informacao, nao decoracao. O que a forma varia e a
 * <i>planta</i> e a silhueta, nunca a altura.
 */
function towerForm(seed: number, floors: number, floorHeight: number,
    kind: DeviceKind = 'GENERIC'): TowerForm {
  const rng = rngFrom(seed);
  const H = floors * floorHeight;

  // A agulha so faz sentido acima de uma certa altura -- numa torre baixa le como um
  // chapeu. Tudo o resto esta disponivel em qualquer altura, porque uma rede domestica
  // tem hosts de 4 a 6 portas e e nessa gama que a variedade tem de se ver.
  const roll = rng();
  // Um gateway e sempre uma agulha: numa rede domestica ha tipicamente um so, e a
  // antena no topo torna-o o marco que se procura primeiro ao olhar para a cidade.
  // Um dispositivo embebido e sempre um prisma compacto -- nao tem porte para mais.
  const style: TowerStyle = kind === 'GATEWAY' ? 'SPIRE'
    : kind === 'IOT' ? 'PRISM'
    : floors >= 9
      ? (roll < 0.34 ? 'SETBACK' : roll < 0.60 ? 'SPIRE' : roll < 0.82 ? 'PRISM' : 'SLAB')
      : (roll < 0.36 ? 'SLAB' : roll < 0.68 ? 'PRISM' : 'SETBACK');

  const rotation = (rng() < 0.5 ? 0 : Math.PI / 2) + (rng() - 0.5) * 0.12;
  const wide = 8 + rng() * 6;
  const thin = 4.5 + rng() * 2.5;

  const tiers: Tier[] = [];
  let crown = 2;

  if (style === 'SLAB') {
    tiers.push({ base: 0, width: wide + 3, depth: thin, height: H });
    crown = 1.5;
  } else if (style === 'PRISM') {
    const side = 7 + rng() * 3;
    tiers.push({ base: 0, width: side, depth: side * (0.8 + rng() * 0.4), height: H });
    crown = 2.5;
  } else if (style === 'SETBACK') {
    const cuts = [0.55 + rng() * 0.1, 0.82 + rng() * 0.06];
    const base = 9 + rng() * 3;
    tiers.push({ base: 0, width: base, depth: base * 0.85, height: H * cuts[0] });
    tiers.push({ base: H * cuts[0], width: base * 0.72, depth: base * 0.62, height: H * (cuts[1] - cuts[0]) });
    tiers.push({ base: H * cuts[1], width: base * 0.48, depth: base * 0.42, height: H * (1 - cuts[1]) });
    crown = 3;
  } else {
    // O mastro do gateway e mais alto e mais fino: e decoracao, nao altura -- a
    // altura da estrutura continua a ser floors x floorHeight.
    const base = (kind === 'GATEWAY' ? 5.5 : 7) + rng() * 2;
    tiers.push({ base: 0, width: base, depth: base * 0.9, height: H * 0.78 });
    tiers.push({ base: H * 0.78, width: base * 0.55, depth: base * 0.5, height: H * 0.22 });
    crown = kind === 'GATEWAY' ? 14 + rng() * 4 : 8 + rng() * 6;
  }

  return { style, windows: style === 'SLAB' ? 'RIBBON' : 'STRIP', tiers, rotation, crown };
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
function TierWindows({ tier, style, color, floorHeight, seed, lit }:
  { tier: Tier, style: WindowStyle, color: string, floorHeight: number, seed: number, lit: boolean }) {

  const ref = useRef<THREE.InstancedMesh>(null);
  const floors = Math.max(1, Math.round(tier.height / floorHeight));

  const instances = useMemo(() => {
    const rng = rngFrom(seed);
    const out: {
      pos: [number, number, number], rotY: number,
      scaleX: number, scaleY: number, brightness: number,
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
      const scaleY = style === 'RIBBON' ? floorHeight * 0.26 : floorHeight * 0.62;
      for (let floor = 0; floor < floors; floor++) {
        for (let col = 0; col < cols; col++) {
          // As faixas desenham-se sempre; os frisos sao esparsos de proposito.
          if (style === 'STRIP' && rng() > 0.82) { continue; }
          const local = cols === 1 ? 0 : (col - (cols - 1) / 2) * (span / cols);
          const y = tier.base + floor * floorHeight + floorHeight / 2;
          out.push({
            pos: face.along === 'x'
              ? [local, y, face.sign * outward]
              : [face.sign * outward, y, local],
            rotY: face.rotY,
            scaleX,
            scaleY,
            // Poucas muito acesas, muitas apagadas: e o contraste que da vida.
            brightness: rng() < 0.22 ? 0.85 + rng() * 0.15 : 0.12 + rng() * 0.18,
          });
        }
      }
    }
    return out;
  }, [tier, style, floorHeight, seed, floors]);

  useLayoutEffect(() => {
    const mesh = ref.current;
    if (!mesh) { return; }
    const dummy = new THREE.Object3D();
    const tint = new THREE.Color();
    instances.forEach((instance, i) => {
      dummy.position.set(...instance.pos);
      dummy.rotation.set(0, instance.rotY, 0);
      // A geometria e um quadrado de 1x1; cada instancia estica-se para a sua face.
      dummy.scale.set(instance.scaleX, instance.scaleY, 1);
      dummy.updateMatrix();
      mesh.setMatrixAt(i, dummy.matrix);
      mesh.setColorAt(i, tint.set(color).multiplyScalar(lit ? instance.brightness : 0.18));
    });
    mesh.instanceMatrix.needsUpdate = true;
    if (mesh.instanceColor) { mesh.instanceColor.needsUpdate = true; }
  }, [instances, color, lit]);

  if (instances.length === 0) { return null; }

  return (
    <instancedMesh ref={ref} args={[undefined, undefined, instances.length]}>
      <planeGeometry args={[1, 1]} />
      <meshBasicMaterial color={color} toneMapped={false} transparent opacity={0.95} />
    </instancedMesh>
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
        <mesh key={`tier-core-${i}`} position={[0, cy, 0]} rotation={[0, form.rotation, 0]}
             >
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
                       lit={!isRuin && detail >= DETAIL.FULL} />
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
    // Heliporto circular no telhado
    elements.push(
      <mesh key="helipad" position={[0, H + 3.1, 0]} rotation={[-Math.PI / 2, 0, 0]}>
        <ringGeometry args={[2, 3, 16]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={0.8} />
      </mesh>
    );
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
    // Anel estrutural de suporte a meia-altura
    elements.push(
      <mesh key="overhang-ring" position={[0, H * 0.5, 0]} rotation={[Math.PI / 2, 0, 0]}>
        <ringGeometry args={[towerW - 1, towerW + 2, 4]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.4 : 0.8} side={2} />
      </mesh>
    );
  }

  // 4. CRITICAL: Perigo, Opressão, Controlo Total
  if (riskBand === 'CRITICAL' && portCount > 3) {
    // Anéis Duplos Opressivos flutuando sobre a cidade
    elements.push(
      <mesh key="overhang-ring-1" position={[0, H * 0.7, 0]} rotation={[Math.PI / 2, 0, 0]}>
        <ringGeometry args={[towerW, towerW + 5, 8]} />
        <meshBasicMaterial color={activeColor} wireframe={true} transparent={true} opacity={isRuin ? 0.6 : 0.9} side={2} />
      </mesh>,
      <mesh key="overhang-ring-2" position={[0, H * 0.9, 0]} rotation={[Math.PI / 2, 0, 0]}>
        <ringGeometry args={[towerW, towerW + 3, 8]} />
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

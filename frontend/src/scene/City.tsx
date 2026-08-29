import { EffectComposer, Bloom } from '@react-three/postprocessing';
import { Building } from './Building';
import { StreetControls } from './StreetControls';
import { StreetLayout } from './StreetLayout';



// SCALE a 13 para reduzir a distância entre casas/prédios (sem mexer no tamanho deles)
const SCALE = 13; 

export function City({ 
  onSelectHost, 
  selectedHost,
  scanData
}: { 
  onSelectHost: (host: any) => void, 
  selectedHost: any,
  scanData: any
}) {
  const backendSpacing = scanData.layout?.spacing || 1.0;
  // Forçamos uma cidade com pelo menos 16x16 quarteirões
  // Se a rede tiver apenas 1 PC, não queremos que o mapa seja uma linha de 1D!
  const gridWidth = Math.max(scanData.layout.width / backendSpacing, 16);
  const gridDepth = Math.max(scanData.layout.depth / backendSpacing, 16);
  
  const offsetX = -gridWidth / 2;
  const offsetZ = -gridDepth / 2;

  return (
    <>
      <StreetControls scanData={scanData} />
      <ambientLight intensity={0.1} />

      {/* Ruas, Passeios, Linhas e Candeeiros 100% Enquadrados */}
      <StreetLayout scanData={scanData} />

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
        />
      ))}

      {/* Renderização das Ruínas (Hosts que desapareceram) */}
      {scanData.ruins.map((ruin: any) => (
        <Building
          key={`${scanData.id}-ruin-${ruin.ip}`}
          label={ruin.ip}
          x={((ruin.position.x / backendSpacing) + offsetX) * SCALE}
          z={((ruin.position.z / backendSpacing) + offsetZ) * SCALE}
          // Ruinas por defeito podem assumir aspeto estilhaçado ou base de casa
          portCount={2} 
          riskBand={ruin.riskBand as any}
          isRuin={true}
          onClick={() => onSelectHost(ruin)}
          hostData={ruin}
          isSelected={selectedHost?.ip === ruin.ip}
          onClose={() => onSelectHost(null)}
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

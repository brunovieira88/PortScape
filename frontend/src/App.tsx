import { Canvas } from '@react-three/fiber';
import { Suspense, useState } from 'react';
import { City } from './scene/City';
import { ErrorBoundary } from './ErrorBoundary';
import mockData from './mock/sample-scan.json';
import { startScan, getScan } from './api/client';

export default function App() {
  const [selectedHost, setSelectedHost] = useState<any | null>(null);
  const [scanData, setScanData] = useState<any>(mockData);
  const [isScanning, setIsScanning] = useState(false);
  const [scanStatus, setScanStatus] = useState<string>('');
  const [targetIp, setTargetIp] = useState<string>('');
  const [showMenu, setShowMenu] = useState(true); // Menu no centro do ecrã

  if (selectedHost) {
    window.onkeydown = (e) => {
      if (e.key === 'Escape') setSelectedHost(null);
    };
  }

  const handleStartScan = async () => {
    try {
      setIsScanning(true);
      setScanStatus('INITIALIZING SCAN...');
      const res = await startScan(targetIp || undefined);
      
      const poll = setInterval(async () => {
        try {
          const pollRes = await getScan(res.id);
          setScanStatus(`SCAN STATUS: ${pollRes.status}`);
          
          if (pollRes.status === 'DONE' || pollRes.status === 'FAILED') {
            clearInterval(poll);
            setIsScanning(false);
            if (pollRes.status === 'DONE') {
              setScanData(pollRes);
              setSelectedHost(null);
              setShowMenu(false); // Esconde o menu para mostrar a cidade
            }
          }
        } catch (e) {
          console.error(e);
        }
      }, 2000);

    } catch (e) {
      console.error(e);
      setScanStatus('ERROR STARTING SCAN');
      setIsScanning(false);
    }
  };

  return (
    <div className="w-screen h-screen bg-black overflow-hidden relative font-sans text-white select-none">
      
      {/* Top Left - Título Minimalista Estilo RuView */}
      <div className="absolute top-6 left-8 z-[999] pointer-events-none">
        <h1 className="text-2xl font-bold text-[#00f0ff] tracking-widest flex items-center gap-2 font-mono">
          PortScape
        </h1>
        <p className="text-[10px] text-[#00f0ff]/50 tracking-[0.2em] mt-1 uppercase font-mono">
          Network Audit Observatory
        </p>
      </div>

      {/* Botão de Novo Scan (Canto Inferior Esquerdo) para voltar ao menu */}
      {!showMenu && (
        <button 
          onClick={() => setShowMenu(true)}
          className="absolute bottom-8 left-8 z-[999] bg-[#030d12]/80 backdrop-blur-md border border-[#00f0ff]/30 text-[#00f0ff] px-6 py-3 rounded-full text-xs font-bold tracking-widest uppercase hover:bg-[#00f0ff]/10 hover:shadow-[0_0_15px_rgba(0,240,255,0.2)] transition-all"
        >
          New Network Scan
        </button>
      )}

      {/* Painel Central de Controlo de Scans (Backend) */}
      {showMenu && (
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 z-[999] bg-[#030d12]/90 backdrop-blur-2xl border border-white/10 p-8 w-[400px] rounded-3xl shadow-[0_0_50px_rgba(0,240,255,0.1)] flex flex-col items-center">
          
          <div className="w-16 h-16 rounded-full bg-[#00f0ff]/10 border border-[#00f0ff]/30 flex items-center justify-center mb-6 shadow-[0_0_20px_rgba(0,240,255,0.2)]">
            <svg className="w-8 h-8 text-[#00f0ff]" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" /></svg>
          </div>

          <h2 className="text-2xl font-light mb-1 text-white">Target Interface</h2>
          <p className="text-xs text-gray-500 font-mono mb-8 text-center">Enter IP subnet or leave blank for local network auto-discovery.</p>
          
          <input 
            type="text" 
            placeholder="e.g. 192.168.1.0/24"
            className="w-full bg-black/50 border border-white/10 text-white text-center text-sm p-4 rounded-xl mb-4 outline-none focus:border-[#00f0ff]/50 transition-colors placeholder:text-gray-600 font-mono"
            value={targetIp}
            onChange={e => setTargetIp(e.target.value)}
            disabled={isScanning}
          />

          <button 
            onClick={handleStartScan}
            disabled={isScanning}
            className={`w-full py-4 rounded-xl text-sm font-bold tracking-widest uppercase transition-all
              ${isScanning 
                ? 'bg-white/5 text-white/30 cursor-not-allowed border border-white/5' 
                : 'bg-[#00f0ff] text-black hover:bg-white hover:shadow-[0_0_20px_rgba(0,240,255,0.4)]'
              }`}
          >
            {isScanning ? 'Scanning Network...' : 'Initiate Scan'}
          </button>

          {isScanning && (
            <div className="mt-6 text-xs font-mono text-[#00f0ff] animate-pulse uppercase tracking-widest">
              {scanStatus}
            </div>
          )}

          {!isScanning && (
            <button 
              onClick={() => setShowMenu(false)}
              className="mt-6 text-xs text-gray-500 hover:text-white transition-colors"
            >
              Cancel / View Map
            </button>
          )}
        </div>
      )}

      {/* R3F 3D Canvas */}
      <ErrorBoundary>
        <Canvas gl={{ antialias: false, toneMapping: 0 }}>
          <Suspense fallback={null}>
            <City onSelectHost={setSelectedHost} selectedHost={selectedHost} scanData={scanData} />
          </Suspense>
        </Canvas>
      </ErrorBoundary>
    </div>
  );
}

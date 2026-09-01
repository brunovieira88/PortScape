import { Canvas } from '@react-three/fiber';
import { Suspense, useState, useEffect, useRef } from 'react';
import { City } from './scene/City';
import { ErrorBoundary } from './ErrorBoundary';
import mockData from './mock/sample-scan.json';
import { startScan, getScan, listScans, ApiError } from './api/client';
import { DeviceListPanel } from './ui/DeviceListPanel';
import { HostDetailsModal } from './ui/HostDetailsModal';
import { HistoryPanel } from './ui/HistoryPanel';

export default function App() {
  const [selectedHost, setSelectedHost] = useState<any | null>(null);
  const [detailedHost, setDetailedHost] = useState<any | null>(null);
  const [isHistoryOpen, setIsHistoryOpen] = useState(false);
  const [isInventoryOpen, setIsInventoryOpen] = useState(false);
  const [scanData, setScanData] = useState<any>(mockData);
  const [isScanning, setIsScanning] = useState(false);
  const [scanStatus, setScanStatus] = useState<string>('');
  // O progresso vem do backend. Ja foi uma curva inventada que assintotava nos 95%,
  // enquanto o campo `progress` real chegava em cada sondagem e era deitado fora.
  const [progress, setProgress] = useState<number>(0);
  const [scanError, setScanError] = useState<string | null>(null);
  const [targetIp, setTargetIp] = useState<string>('');
  const [showMenu, setShowMenu] = useState(true); // Menu no centro do ecrã

  // Sondagem e temporizadores em curso, para poderem ser cancelados no desmonte.
  const poll = useRef<ReturnType<typeof setInterval> | null>(null);
  // Cada coisa que possa vir a pintar o ecra -- um scan carregado do historico ou um
  // scan novo a decorrer -- leva um numero. So o mais recente escreve. Sem isto,
  // clicar em dois scans seguidos deixava a resposta mais lenta sobrepor-se, e um scan
  // a decorrer roubava o ecra a um scan antigo que o utilizador tivesse aberto no meio.
  const loadSeq = useRef(0);

  const reveal = useRef<ReturnType<typeof setTimeout> | null>(null);

  const stopPolling = () => {
    if (poll.current) {
      clearInterval(poll.current);
      poll.current = null;
    }
    if (reveal.current) {
      clearTimeout(reveal.current);
      reveal.current = null;
    }
  };

  useEffect(() => {
    listScans()
      .then(scans => (scans?.length ? getScan(scans[0].id) : null))
      .then(latest => { if (latest) setScanData(latest); })
      // Sem backend fica-se no cenario de exemplo, que e o comportamento util aqui.
      .catch(() => {});
    return stopPolling;
  }, []);

  useEffect(() => {
    if (!selectedHost) { return; }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setSelectedHost(null); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [selectedHost]);

  const handleLoadScan = async (id: string) => {
    const seq = ++loadSeq.current;
    try {
      const fullScan = await getScan(id);
      if (seq !== loadSeq.current) { return; }
      setScanData(fullScan);
      setSelectedHost(null);
      setShowMenu(false);
      setScanError(null);
    } catch (e) {
      if (seq === loadSeq.current) {
        setScanError(e instanceof ApiError ? e.message : 'Não foi possível carregar o scan.');
      }
    }
  };

  const handleStartScan = async () => {
    stopPolling();
    const seq = ++loadSeq.current;
    setIsScanning(true);
    setScanError(null);
    setScanStatus('INITIALIZING SCAN...');
    setProgress(0);

    let started;
    try {
      started = await startScan(targetIp || undefined);
    } catch (e) {
      // O backend explica-se: alvo fora de uma rede privada, fila cheia, nmap sem
      // permissoes. Mostrar essa mensagem em vez de um erro generico e a diferenca
      // entre o utilizador perceber o que fez e ficar a adivinhar.
      setScanError(e instanceof ApiError ? e.message : 'Não foi possível iniciar o scan.');
      setScanStatus('');
      setIsScanning(false);
      return;
    }

    poll.current = setInterval(async () => {
      try {
        const current = await getScan(started.id);
        // O utilizador abriu outro scan entretanto: deixa-lo ver o que escolheu.
        if (seq !== loadSeq.current) { stopPolling(); return; }
        setScanStatus(`SCAN STATUS: ${current.status}`);
        setProgress(current.progress ?? 0);

        if (current.status !== 'DONE' && current.status !== 'FAILED') { return; }
        stopPolling();

        if (current.status === 'FAILED') {
          setScanError(current.error?.message || 'O scan falhou.');
          setIsScanning(false);
          return;
        }

        setProgress(100);
        // Pequeno atraso para a barra se ver a chegar aos 100%.
        reveal.current = setTimeout(() => {
          setIsScanning(false);
          if (seq !== loadSeq.current) { return; }
          setScanData(current);
          setSelectedHost(null);
          setShowMenu(false);
        }, 600);
      } catch (e) {
        // Um scan apagado a meio, ou o backend em baixo: parar em vez de sondar
        // para sempre, que era o que acontecia antes.
        stopPolling();
        setScanError(e instanceof ApiError ? e.message : 'Perdeu-se o contacto com o scan.');
        setIsScanning(false);
      }
    }, 1500);
  };

  const anyPanelOpen = isHistoryOpen || isInventoryOpen;

  return (
    <div className="w-screen h-screen bg-black overflow-hidden relative font-sans text-white select-none">
      
      {/* AVISO DE MOCK DATA GLOBAL */}
      {scanData.id === '11111111-2222-3333-4444-555555555555' && (
        <div className="absolute top-0 left-0 w-full bg-red-600/90 text-white font-mono text-[10px] sm:text-xs text-center py-2 z-[9999] tracking-[0.3em] font-bold shadow-[0_0_30px_rgba(255,0,0,0.8)] border-b border-red-500 uppercase flex justify-center items-center gap-4">
          <span className="animate-pulse">⚠️</span>
          SIMULATION MODE: DISPLAYING OFFLINE MOCK DATA. INITIATE A REAL SCAN TO OBSERVE ACTUAL NETWORK TOPOLOGY.
          <span className="animate-pulse">⚠️</span>
        </div>
      )}

      {/* Top Left - Título Minimalista Estilo RuView */}
      <div className={`absolute left-8 z-[999] pointer-events-none transition-all duration-300 ${scanData.id === '11111111-2222-3333-4444-555555555555' ? 'top-14' : 'top-6'} ${anyPanelOpen || showMenu ? 'opacity-0' : 'opacity-100'}`}>
        <h1 className="text-2xl font-bold text-[#00f0ff] tracking-widest flex items-center gap-2 font-mono">
          PortScape
        </h1>
        <p className="text-[10px] text-[#00f0ff]/50 tracking-[0.2em] mt-1 uppercase font-mono">
          Network Audit Observatory
        </p>
      </div>

      {/* Botão de Novo Scan (Canto Inferior Esquerdo) para voltar ao menu */}
      {/* Botão de Novo Scan */}
      <div className={`transition-opacity duration-300 ${showMenu || anyPanelOpen ? 'opacity-0 pointer-events-none' : 'opacity-100'}`}>
        <button 
          onClick={() => setShowMenu(true)}
          className="absolute top-8 right-16 z-[9999] group overflow-hidden bg-[#030d12]/95 backdrop-blur-xl border border-[#00f0ff]/40 text-[#00f0ff] px-6 py-2.5 font-mono text-[10px] font-bold tracking-[0.2em] uppercase hover:bg-[#00f0ff]/10 transition-all duration-300 shadow-[0_0_20px_rgba(0,240,255,0.15)] hover:shadow-[0_0_40px_rgba(0,240,255,0.4)]"
          style={{ clipPath: 'polygon(10px 0, 100% 0, 100% calc(100% - 10px), calc(100% - 10px) 100%, 0 100%, 0 10px)' }}
        >
          <div className="absolute inset-0 w-[200%] h-full bg-gradient-to-r from-transparent via-[#00f0ff]/30 to-transparent -translate-x-full group-hover:animate-[sweep_1.5s_ease-in-out_infinite]"></div>
          <div className="flex items-center gap-3 relative z-10">
            <div className="relative w-4 h-4 flex items-center justify-center">
              <div className="absolute inset-0 border border-[#00f0ff]/50 rounded-full"></div>
              <div className="absolute inset-0 border-t-2 border-[#00f0ff] rounded-full animate-[spin_2s_linear_infinite]"></div>
              <div className="absolute w-1 h-1 bg-[#00f0ff] rounded-full animate-pulse"></div>
            </div>
            <span className="group-hover:text-white transition-colors">INITIATE SCAN</span>
          </div>
          <div className="absolute top-0 right-2 w-4 h-[1px] bg-[#00f0ff]/80"></div>
          <div className="absolute bottom-0 left-2 w-4 h-[1px] bg-[#00f0ff]/80"></div>
        </button>
      </div>

      {/* Se a consulta ao NVD falhou ou bateu no limite de pedidos, os CVEs vieram
          incompletos e o risco mostrado esta SUBESTIMADO. Numa ferramenta de auditoria
          isso tem de ser dito: mostrar risco a menos sem avisar e o pior silencio. */}
      {scanData?.cveLookupDegraded && !showMenu && !anyPanelOpen && (
        <div className="absolute bottom-8 left-1/2 -translate-x-1/2 z-[997] bg-[#030d12]/95 backdrop-blur-xl border border-[#ff8a00]/40 px-5 py-3 rounded-xl flex items-center gap-3 shadow-[0_0_30px_rgba(255,138,0,0.15)]">
          <span className="text-[#ff8a00]">⚠</span>
          <div className="text-[10px] font-mono uppercase tracking-widest text-[#ff8a00]">
            CVE lookup degraded — risk scores may be understated
          </div>
        </div>
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
            <div className="mt-6 w-full flex flex-col items-center">
              <div className="text-[10px] font-mono text-[#00f0ff] mb-2 uppercase tracking-widest flex justify-between w-full px-1">
                <span>{scanStatus}</span>
                <span>{Math.round(progress)}%</span>
              </div>
              <div className="w-full h-1 bg-white/10 rounded-full overflow-hidden">
                {/* A transicao longa e o que suaviza os saltos entre sondagens: o
                    valor mostrado e sempre o do backend, so chega la a deslizar. */}
                <div 
                  className="h-full bg-[#00f0ff] shadow-[0_0_10px_#00f0ff] transition-[width] duration-1000 ease-out" 
                  style={{ width: `${progress}%` }}
                ></div>
              </div>
            </div>
          )}

          {scanError && (
            <div className="mt-6 w-full bg-[#ff003c]/10 border border-[#ff003c]/40 rounded-lg p-3 flex items-start gap-3">
              <span className="text-[#ff003c] text-sm leading-none mt-0.5">⚠</span>
              <div className="text-[11px] text-[#ff8a9f] leading-relaxed">{scanError}</div>
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

      {/* Painel de Histórico (Canto Esquerdo) */}
      <HistoryPanel 
        onScanDeleted={(id: string) => {
          // A cidade estava a mostrar este scan: sem isto ficava um fantasma no ecra,
          // e voltar a clicar nele dava 404.
          if (scanData?.id === id) {
            loadSeq.current++;
            stopPolling();
            setScanData(mockData);
            setSelectedHost(null);
            setDetailedHost(null);
          }
        }}

        activeScanId={scanData?.id} 
        onSelectScan={handleLoadScan} 
        isOpen={isHistoryOpen} 
        onToggle={() => setIsHistoryOpen(!isHistoryOpen)} 
        isHidden={isInventoryOpen || showMenu}
      />

      {/* Painel Lateral com Lista de Dispositivos */}
      <DeviceListPanel 
        scanData={scanData} 
        onOpenDetails={setDetailedHost} 
        isOpen={isInventoryOpen}
        onToggle={() => setIsInventoryOpen(!isInventoryOpen)}
        isHidden={isHistoryOpen || showMenu}
      />

      {/* Modal de Detalhes Completos do Host */}
      {detailedHost && (
        <HostDetailsModal host={detailedHost} onClose={() => setDetailedHost(null)} />
      )}

      {/* Overlay Escuro para focar nos menus */}
      <div 
        className={`absolute inset-0 z-[997] transition-all duration-500 pointer-events-none ${anyPanelOpen ? 'bg-black/40 backdrop-blur-[2px] pointer-events-auto' : 'bg-transparent backdrop-blur-none'}`}
        onClick={() => { setIsHistoryOpen(false); setIsInventoryOpen(false); }}
      ></div>

      {/* R3F 3D Canvas */}
      <ErrorBoundary>
        <Canvas gl={{ antialias: true, toneMapping: 0 }} camera={{ near: 0.5, far: 2000, fov: 60 }}>
          <Suspense fallback={null}>
            <City 
              
              selectedHost={selectedHost} onSelectHost={setSelectedHost} 
              scanData={scanData} 
              onOpenDetails={setDetailedHost} 
            />
          </Suspense>
        </Canvas>
      </ErrorBoundary>

      <style>{`
        @keyframes sweep {
          0% { transform: translateX(-100%); }
          100% { transform: translateX(50%); }
        }
      `}</style>
    </div>
  );
}

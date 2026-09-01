import { useEffect, useRef, useState } from 'react';
import { listScans, deleteScan, ApiError } from '../api/client';

function CrumblingBlocks() {
  const blocks = Array.from({ length: 24 }); // 6 colunas x 4 linhas
  return (
    <div className="absolute inset-0 flex flex-wrap" style={{ transformStyle: 'preserve-3d' }}>
      {blocks.map((_, i) => {
        const tx = (Math.random() - 0.5) * 150; // espalha no X
        const ty = Math.random() * 100 + 50;    // cai no Y (gravidade)
        const tz = (Math.random() - 0.5) * 100; // profundidade
        const rX = (Math.random() - 0.5) * 720;
        const rY = (Math.random() - 0.5) * 720;
        const rZ = (Math.random() - 0.5) * 720;
        
        return (
          <div 
            key={i} 
            className="w-1/6 h-1/4 bg-[#030d12] border border-[#00f0ff]/20 animate-brick-fall"
            style={{
              '--tx': `${tx}px`,
              '--ty': `${ty}px`,
              '--tz': `${tz}px`,
              '--rx': `${rX}deg`,
              '--ry': `${rY}deg`,
              '--rz': `${rZ}deg`,
            } as any}
          />
        );
      })}
    </div>
  );
}

export function HistoryPanel({ activeScanId, onSelectScan, onScanDeleted, isOpen, onToggle, isHidden }: { activeScanId?: string, onSelectScan: (id: string) => void, onScanDeleted?: (id: string) => void, isOpen: boolean, onToggle: () => void, isHidden?: boolean }) {
  const [scans, setScans] = useState<any[]>([]);
  const [scanToDelete, setScanToDelete] = useState<string | null>(null);
  const [scanToDestroy, setScanToDestroy] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const alive = useRef(true);
  const destruction = useRef<ReturnType<typeof setTimeout> | null>(null);

  const fetchScans = () => {
    listScans()
      .then(list => { if (alive.current) { setScans(list); setLoadError(null); } })
      .catch(err => {
        // Sem isto, um backend em baixo dava um historico vazio indistinguivel de
        // "ainda nao ha scans" -- e ninguem percebia que o problema era a ligacao.
        if (alive.current) {
          setLoadError(err instanceof ApiError ? err.message : 'Histórico indisponível.');
        }
      });
  };

  useEffect(() => {
    fetchScans();
  }, [activeScanId]);

  useEffect(() => () => {
    alive.current = false;
    if (destruction.current) { clearTimeout(destruction.current); }
  }, []);

  const handleDeleteConfirm = async (e: React.MouseEvent, id: string) => {
    e.stopPropagation(); 
    setScanToDestroy(id);
    
    // O atraso e so para a animacao de demolicao correr antes da linha desaparecer.
    destruction.current = setTimeout(async () => {
      try {
        await deleteScan(id);
        if (!alive.current) { return; }
        setScanToDelete(null);
        setScanToDestroy(null);
        onScanDeleted?.(id);
        fetchScans(); 
      } catch (err) {
        if (!alive.current) { return; }
        setScanToDestroy(null);
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível apagar o scan.');
      }
    }, 800);
  };

  return (
    <div className={`transition-opacity duration-300 ${isHidden ? 'opacity-0 pointer-events-none' : 'opacity-100'}`}>
      <div 
        className={`absolute top-0 left-0 h-full w-[350px] bg-[#030d12]/95 backdrop-blur-2xl border-r border-[#00f0ff]/30 z-[998] transform transition-transform duration-300 shadow-[10px_0_30px_rgba(0,240,255,0.05)] flex flex-col ${
          isOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <button 
          onClick={onToggle}
          className="absolute top-1/2 right-0 translate-x-full -translate-y-1/2 bg-[#030d12]/95 backdrop-blur-xl border border-[#00f0ff]/40 border-l-0 text-[#00f0ff] px-2.5 py-12 text-[10px] font-mono font-bold tracking-[0.3em] hover:bg-[#00f0ff]/20 hover:text-white transition-all shadow-[5px_0_20px_rgba(0,240,255,0.1)] hover:shadow-[10px_0_30px_rgba(0,240,255,0.3)] flex flex-col items-center justify-center gap-4 group"
          style={{ clipPath: 'polygon(0 0, 100% 10px, 100% calc(100% - 10px), 0 100%)' }}
        >
          {/* Decorações Topo */}
          <div className="flex flex-col gap-1.5 opacity-40 group-hover:opacity-100 transition-opacity">
             <div className="w-1.5 h-1.5 bg-[#00f0ff]"></div>
             <div className="w-1.5 h-1.5 border border-[#00f0ff]"></div>
          </div>
          
          <span className="[writing-mode:vertical-lr] uppercase">
            {isOpen ? 'CLOSE' : 'HISTORY'}
          </span>
          
          {/* Decorações Fundo */}
          <div className="flex flex-col gap-1.5 opacity-40 group-hover:opacity-100 transition-opacity">
             <div className="w-1.5 h-1.5 border border-[#00f0ff]"></div>
             <div className="w-1.5 h-1.5 bg-[#00f0ff]"></div>
          </div>

          {/* Fio de néon na borda externa */}
          <div className="absolute top-0 right-0 w-[1px] h-full bg-[#00f0ff]/50 group-hover:bg-[#00f0ff] transition-colors"></div>
        </button>

        {/* Efeito de Scanlines Globais */}
        <div className="absolute inset-0 pointer-events-none opacity-[0.03] bg-[linear-gradient(transparent_50%,#000_50%)] bg-[length:100%_4px] z-0"></div>

        <div className="relative p-6 border-b border-[#00f0ff]/20 pt-24 overflow-hidden">
          {/* Fundo listrado subtil do cabeçalho */}
          <div className="absolute inset-0 opacity-10 bg-[repeating-linear-gradient(45deg,transparent,transparent_2px,#00f0ff_2px,#00f0ff_4px)] [mask-image:linear-gradient(to_bottom,black,transparent)]"></div>
          
          <h2 className="text-[#00f0ff] text-xl font-mono uppercase tracking-widest flex items-center gap-2 relative z-10">
            <span className="w-2 h-4 bg-[#00f0ff] animate-pulse"></span>
            History
          </h2>
          <p className="text-[#00f0ff]/50 text-[10px] font-mono mt-1 uppercase tracking-widest relative z-10">
            Time Travel Archives
          </p>
        </div>

        <div className="flex-1 overflow-y-auto p-4 custom-scrollbar pointer-events-auto overflow-x-hidden">
          <div className="flex flex-col">
            {scans.map(scan => {
              const isActive = scan.id === activeScanId;
              const isDeleting = scanToDelete === scan.id;
              const isDestroying = scanToDestroy === scan.id;
              const date = new Date(scan.createdAt).toLocaleString(undefined, { 
                month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' 
              });
              
              return (
                <div 
                  key={scan.id}
                  onClick={() => {
                    if (!isDeleting && !isDestroying) onSelectScan(scan.id);
                  }}
                  className={`relative mb-2 p-3 border group ${
                    isActive && !isDestroying
                      ? 'bg-[#00f0ff]/10 border-[#00f0ff]/50' 
                      : 'bg-black/60 border-[#00f0ff]/10 hover:border-[#00f0ff]/50 cursor-pointer'
                  } ${isDeleting && !isDestroying ? 'border-red-500/30 bg-red-950/20 cursor-default' : 'cursor-pointer'} 
                  ${isDestroying ? 'animate-collapse-gap border-transparent pointer-events-none' : 'overflow-hidden transition-colors'}`}
                  style={!isDestroying ? { clipPath: 'polygon(8px 0, 100% 0, 100% calc(100% - 8px), calc(100% - 8px) 100%, 0 100%, 0 8px)' } : {}}
                >
                  {!isDestroying && (
                    <div className={`absolute top-0 left-0 h-[1px] transition-all duration-500 ${isActive ? 'w-full bg-[#00f0ff]' : 'w-8 bg-[#00f0ff]/30 group-hover:w-full group-hover:bg-[#00f0ff]'}`}></div>
                  )}
                  
                  {isDestroying && <CrumblingBlocks />}

                  <div className={`transition-opacity duration-200 ${isDestroying ? 'opacity-0' : 'opacity-100'}`}>
                    <div className="flex justify-between items-center mb-1">
                      <span className={`text-xs font-mono font-bold transition-colors ${isActive ? 'text-[#00f0ff]' : 'text-gray-300'}`}>
                        {date}
                      </span>
                      <div className="flex items-center gap-2 h-4">
                        
                        {!isDeleting && (
                          <>
                            {scan.status === 'DONE' && (
                              <span className="text-[9px] bg-green-500/20 text-green-400 px-1.5 py-0.5 rounded font-mono uppercase">
                                {scan.hostsUp} Hosts
                              </span>
                            )}
                            {scan.status === 'FAILED' && (
                              <span className="text-[9px] bg-red-500/20 text-red-400 px-1.5 py-0.5 rounded font-mono uppercase">Error</span>
                            )}
                            <button 
                              onClick={(e) => { e.stopPropagation(); setScanToDelete(scan.id); }}
                              className="text-gray-500 hover:text-red-500 transition-colors opacity-0 group-hover:opacity-100"
                              title="Delete Scan"
                            >
                              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
                            </button>
                          </>
                        )}

                        {isDeleting && !isDestroying && (
                          <div className="flex items-center gap-1.5 bg-red-950/90 px-1.5 py-0.5 rounded border border-red-500/40">
                            <span className="text-[9px] text-red-400 font-bold uppercase tracking-wider pr-1">Sure?</span>
                            <button 
                              onClick={(e) => handleDeleteConfirm(e, scan.id)}
                              className="text-red-400 hover:text-white transition-colors"
                              title="Confirm"
                            >
                              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 13l4 4L19 7" /></svg>
                            </button>
                            <button 
                              onClick={(e) => { e.stopPropagation(); setScanToDelete(null); }}
                              className="text-gray-400 hover:text-white transition-colors ml-1"
                              title="Cancel"
                            >
                              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M6 18L18 6M6 6l12 12" /></svg>
                            </button>
                          </div>
                        )}
                      </div>
                    </div>
                    <div className="text-[10px] text-gray-500 font-mono truncate">
                      {scan.target || 'Auto-Detected'}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
          {loadError ? (
            <div className="mt-10 mx-2 bg-[#ff003c]/10 border border-[#ff003c]/40 rounded p-3 text-center">
              <div className="text-[#ff003c] mb-1">⚠</div>
              <div className="text-[10px] font-mono text-[#ff8a9f] leading-relaxed">{loadError}</div>
            </div>
          ) : scans.length === 0 && (
            <div className="text-xs text-gray-500 text-center mt-10 font-mono">No historical records</div>
          )}
        </div>
      </div>
      
      <style>{`
        .custom-scrollbar::-webkit-scrollbar {
          width: 4px;
        }
        .custom-scrollbar::-webkit-scrollbar-track {
          background: rgba(0, 0, 0, 0.2);
        }
        .custom-scrollbar::-webkit-scrollbar-thumb {
          background: rgba(0, 240, 255, 0.3);
          border-radius: 4px;
        }
        .custom-scrollbar::-webkit-scrollbar-thumb:hover {
          background: rgba(0, 240, 255, 0.6);
        }

        @keyframes brick-fall {
          0% { 
            transform: translate3d(0, 0, 0) rotate3d(0,0,0,0deg); 
            opacity: 1; 
          }
          100% { 
            transform: translate3d(var(--tx), var(--ty), var(--tz)) rotateX(var(--rx)) rotateY(var(--ry)) rotateZ(var(--rz)); 
            opacity: 0; 
          }
        }
        .animate-brick-fall {
          animation: brick-fall 0.8s ease-in forwards;
        }

        @keyframes collapse-gap {
          0% { max-height: 100px; margin-bottom: 0.5rem; background-color: transparent; }
          40% { max-height: 100px; margin-bottom: 0.5rem; background-color: transparent; }
          100% { max-height: 0px; margin-bottom: 0; padding-top: 0; padding-bottom: 0; border-width: 0; background-color: transparent; }
        }
        .animate-collapse-gap {
          animation: collapse-gap 0.8s forwards;
        }
      `}</style>
    </div>
  );
}

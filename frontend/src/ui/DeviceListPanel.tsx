import { BAND_COLORS } from '../scene/Building';

function bandColor(band: string): string {
  return BAND_COLORS[band as keyof typeof BAND_COLORS] || BAND_COLORS.UNKNOWN;
}

export function DeviceListPanel({ scanData, onOpenDetails, isOpen, onToggle, isHidden }: { scanData: any, onOpenDetails?: (host: any) => void, isOpen: boolean, onToggle: () => void, isHidden?: boolean }) {
  const activeHosts = scanData?.hosts || [];
  const ruins = scanData?.ruins || [];

  return (
    <div className={`transition-opacity duration-300 ${isHidden ? 'opacity-0 pointer-events-none' : 'opacity-100'}`}>
      {/* Painel Lateral Direito */}
      <div 
        className={`absolute top-0 right-0 h-full w-[350px] bg-[#030d12]/95 backdrop-blur-2xl border-l border-[#00f0ff]/30 z-[998] transform transition-transform duration-300 shadow-[-10px_0_30px_rgba(0,240,255,0.05)] flex flex-col ${
          isOpen ? 'translate-x-0' : 'translate-x-full'
        }`}
      >
        {/* Botão de Toggle ancorado ao painel (desliza com ele) */}
        <button 
          onClick={onToggle}
          className="absolute top-1/2 left-0 -translate-x-full -translate-y-1/2 bg-[#030d12]/95 backdrop-blur-xl border border-[#00f0ff]/40 border-r-0 text-[#00f0ff] px-2.5 py-12 text-[10px] font-mono font-bold tracking-[0.3em] hover:bg-[#00f0ff]/20 hover:text-white transition-all shadow-[-5px_0_20px_rgba(0,240,255,0.1)] hover:shadow-[-10px_0_30px_rgba(0,240,255,0.3)] flex flex-col items-center justify-center gap-4 group"
          style={{ clipPath: 'polygon(0 10px, 100% 0, 100% 100%, 0 calc(100% - 10px))' }}
        >
          {/* Decorações Topo */}
          <div className="flex flex-col gap-1.5 opacity-40 group-hover:opacity-100 transition-opacity">
             <div className="w-1.5 h-1.5 bg-[#00f0ff]"></div>
             <div className="w-1.5 h-1.5 border border-[#00f0ff]"></div>
          </div>
          
          <span className="[writing-mode:vertical-lr] rotate-180 uppercase">
            {isOpen ? 'CLOSE' : 'DEVICES'}
          </span>
          
          {/* Decorações Fundo */}
          <div className="flex flex-col gap-1.5 opacity-40 group-hover:opacity-100 transition-opacity">
             <div className="w-1.5 h-1.5 border border-[#00f0ff]"></div>
             <div className="w-1.5 h-1.5 bg-[#00f0ff]"></div>
          </div>

          {/* Fio de néon na borda externa */}
          <div className="absolute top-0 left-0 w-[1px] h-full bg-[#00f0ff]/50 group-hover:bg-[#00f0ff] transition-colors"></div>
        </button>

        {/* Efeito de Scanlines Globais */}
        <div className="absolute inset-0 pointer-events-none opacity-[0.03] bg-[linear-gradient(transparent_50%,#000_50%)] bg-[length:100%_4px] z-0"></div>

        <div className="relative p-6 border-b border-[#00f0ff]/20 pt-16 overflow-hidden">
          {/* Fundo listrado subtil do cabeçalho */}
          <div className="absolute inset-0 opacity-10 bg-[repeating-linear-gradient(45deg,transparent,transparent_2px,#00f0ff_2px,#00f0ff_4px)] [mask-image:linear-gradient(to_bottom,black,transparent)]"></div>
          
          <h2 className="text-[#00f0ff] text-xl font-mono uppercase tracking-widest flex items-center gap-2 relative z-10">
            <span className="w-2 h-4 bg-[#00f0ff] animate-pulse"></span>
            Inventory
          </h2>
          <p className="text-[#00f0ff]/50 text-[10px] font-mono mt-1 uppercase tracking-widest relative z-10">
            SCAN: {scanData.target || 'AUTO-DETECTED'}
          </p>
        </div>

        <div className="flex-1 overflow-y-auto p-4 space-y-6 custom-scrollbar pointer-events-auto relative z-10">
          
          {/* Active Hosts */}
          <div>
            <div className="flex items-center gap-2 mb-3 border-b border-[#00f0ff]/20 pb-2">
              <span className="text-[#00f0ff] text-xs font-bold tracking-[0.2em] uppercase">Active Targets</span>
              <span className="text-[9px] bg-[#00f0ff]/20 text-[#00f0ff] px-1.5 py-0.5 rounded font-mono">{activeHosts.length}</span>
            </div>
            <div className="flex flex-col gap-2">
              {activeHosts.map((host: any) => (
                <div 
                  key={host.ip} 
                  onClick={() => {
                    if (onOpenDetails) onOpenDetails(host);
                  }}
                  className="relative p-3 bg-black/60 border border-[#00f0ff]/10 hover:border-[#00f0ff]/50 cursor-pointer transition-colors group"
                  style={{ clipPath: 'polygon(8px 0, 100% 0, 100% calc(100% - 8px), calc(100% - 8px) 100%, 0 100%, 0 8px)' }}
                >
                  <div className="absolute top-0 left-0 h-[1px] bg-[#00f0ff]/30 group-hover:bg-[#00f0ff] w-8 group-hover:w-full transition-all duration-500"></div>
                  
                  <div className="flex justify-between items-start mb-1">
                    <span className="text-[#00f0ff] font-mono font-bold">{host.ip}</span>
                    {/* A cor sai do BAND_COLORS, a mesma que pinta o edificio na
                        cidade. Esta lista tinha uma paleta propria, e o LOW aparecia
                        verde aqui e ciano na cena -- duas linguagens de cor para a
                        mesma informacao, o que obriga a reaprender o mapa. */}
                    <span
                      className="text-[10px] px-1.5 py-0.5 rounded font-mono font-bold"
                      style={{
                        color: bandColor(host.riskBand),
                        backgroundColor: `${bandColor(host.riskBand)}22`,
                      }}
                    >
                      {host.riskBand}
                    </span>
                  </div>
                  <div className="text-xs text-gray-400 truncate">
                    {host.hostname?.replace(/\.(home|lan|local)$/i, '') || 'Unknown Host'}
                  </div>
                  <div className="text-[10px] text-gray-500 mt-2 font-mono flex gap-2">
                    <span className="bg-white/5 px-1 py-0.5 rounded">PORTS: <span className="text-gray-300">{host.portCount}</span></span>
                    <span className="bg-white/5 px-1 py-0.5 rounded">OS: <span className="text-gray-300">{host.osGuess || 'UNKNOWN'}</span></span>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Offline / Ruins */}
          {ruins.length > 0 && (
            <div>
              <div className="flex items-center gap-2 mb-3 border-b border-gray-800 pb-2">
                <span className="text-gray-500 text-xs font-bold tracking-[0.2em] uppercase">Offline Relics</span>
                <span className="text-[9px] bg-gray-800 text-gray-400 px-1.5 py-0.5 rounded font-mono">{ruins.length}</span>
              </div>
              <div className="flex flex-col gap-2">
                {ruins.map((ruin: any) => (
                  <div 
                    key={ruin.ip} 
                    onClick={() => {
                      if (onOpenDetails) onOpenDetails(ruin);
                    }}
                    className="relative p-3 bg-black/20 border border-gray-800 opacity-60 hover:opacity-100 hover:border-gray-600 hover:bg-gray-900 cursor-pointer transition-colors group"
                    style={{ clipPath: 'polygon(8px 0, 100% 0, 100% calc(100% - 8px), calc(100% - 8px) 100%, 0 100%, 0 8px)' }}
                  >
                    <div className="absolute top-0 left-0 h-[1px] bg-gray-700 w-8 group-hover:w-full transition-all duration-500"></div>

                    <div className="flex justify-between items-start mb-1">
                      <span className="text-gray-400 font-mono line-through">{ruin.ip}</span>
                      <span className="text-[10px] bg-gray-800 text-gray-400 px-1.5 py-0.5 rounded font-mono">OFFLINE</span>
                    </div>
                    <div className="text-[10px] text-gray-500">
                      {ruin.hostname?.replace(/\.(home|lan|local)$/i, '') || 'Unknown Host'}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

        </div>
      </div>
      
      {/* CSS customizado para a scrollbar do painel */}
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
      `}</style>
    </div>
  );
}

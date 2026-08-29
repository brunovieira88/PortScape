import { useState } from 'react';

export function DeviceListPanel({ scanData, onSelectHost, onOpenDetails }: { scanData: any, onSelectHost: (host: any) => void, onOpenDetails?: (host: any) => void }) {
  const [isOpen, setIsOpen] = useState(false);

  const activeHosts = scanData?.hosts || [];
  const ruins = scanData?.ruins || [];

  return (
    <>
      {/* Painel Lateral Direito */}
      <div 
        className={`absolute top-0 right-0 h-full w-[350px] bg-[#030d12]/95 backdrop-blur-2xl border-l border-[#00f0ff]/30 z-[998] transform transition-transform duration-300 shadow-[-10px_0_30px_rgba(0,240,255,0.05)] flex flex-col ${
          isOpen ? 'translate-x-0' : 'translate-x-full'
        }`}
      >
        {/* Botão de Toggle ancorado ao painel (desliza com ele) */}
        <button 
          onClick={() => setIsOpen(!isOpen)}
          className="absolute top-1/2 left-0 -translate-x-full -translate-y-1/2 bg-[#030d12]/80 backdrop-blur-md border border-[#00f0ff]/30 border-r-0 text-[#00f0ff] p-2 py-8 rounded-l-lg text-xs font-bold tracking-widest hover:bg-[#00f0ff]/20 transition-all shadow-[-5px_0_15px_rgba(0,240,255,0.2)] flex items-center justify-center"
        >
          <span className="[writing-mode:vertical-lr] rotate-180 uppercase tracking-widest">
            {isOpen ? 'Close Inventory' : 'Device Inventory'}
          </span>
        </button>

        <div className="p-6 border-b border-[#00f0ff]/10 pt-16">
          <h2 className="text-[#00f0ff] text-xl font-mono uppercase tracking-widest">Network Inventory</h2>
          <p className="text-gray-400 text-xs font-mono mt-1 uppercase">Scan ID: {scanData.id.split('-')[0]}</p>
        </div>

        <div className="flex-1 overflow-y-auto p-4 space-y-6 custom-scrollbar pointer-events-auto">
          
          {/* Active Hosts */}
          <div>
            <h3 className="text-white text-xs font-bold tracking-[0.2em] uppercase mb-3 border-b border-white/10 pb-2">Active Targets ({activeHosts.length})</h3>
            <div className="space-y-2">
              {activeHosts.map((host: any) => (
                <div 
                  key={host.ip} 
                  onClick={() => {
                    if (onOpenDetails) onOpenDetails(host);
                  }}
                  className="bg-black/50 border border-white/5 p-3 rounded-lg hover:border-[#00f0ff]/50 hover:bg-[#00f0ff]/5 cursor-pointer transition-colors group"
                >
                  <div className="flex justify-between items-start mb-1">
                    <span className="text-[#00f0ff] font-mono font-bold">{host.ip}</span>
                    <span className={`text-[10px] px-1.5 py-0.5 rounded font-mono font-bold
                      ${host.riskBand === 'CRITICAL' ? 'bg-red-500/20 text-red-500' : 
                        host.riskBand === 'HIGH' ? 'bg-orange-500/20 text-orange-500' :
                        host.riskBand === 'MEDIUM' ? 'bg-yellow-500/20 text-yellow-500' :
                        host.riskBand === 'LOW' ? 'bg-green-500/20 text-green-500' :
                        'bg-gray-500/20 text-gray-400'}
                    `}>
                      {host.riskBand}
                    </span>
                  </div>
                  <div className="text-xs text-gray-400 truncate">
                    {host.hostname?.replace(/\.(home|lan|local)$/i, '') || 'Unknown Host'}
                  </div>
                  <div className="text-[10px] text-gray-500 mt-2 font-mono flex gap-2">
                    <span>PORTS: {host.portCount}</span>
                    <span>OS: {host.osGuess || 'UNKNOWN'}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Offline / Ruins */}
          {ruins.length > 0 && (
            <div>
              <h3 className="text-gray-500 text-xs font-bold tracking-[0.2em] uppercase mb-3 border-b border-white/10 pb-2">Offline Relics ({ruins.length})</h3>
              <div className="space-y-2">
                {ruins.map((ruin: any) => (
                  <div 
                    key={ruin.ip} 
                    onClick={() => {
                      if (onOpenDetails) onOpenDetails(ruin);
                    }}
                    className="bg-black/20 border border-gray-800 p-3 rounded-lg opacity-60 hover:border-gray-600 hover:bg-gray-800/50 cursor-pointer transition-colors"
                  >
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
    </>
  );
}

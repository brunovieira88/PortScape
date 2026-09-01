import { useEffect } from 'react';

export function HostDetailsModal({ host, onClose }: { host: any, onClose: () => void }) {
  // Fecha com a tecla ESC
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  if (!host) return null;

  const ports = host.ports || [];
  const riskReasons = host.riskReasons || [];

  return (
    <div className="absolute inset-0 z-[9999] bg-black/80 backdrop-blur-sm flex items-center justify-center p-8">
      {/* Modal Container */}
      <div className="bg-[#030d12] border border-[#00f0ff]/30 rounded-xl shadow-[0_0_50px_rgba(0,240,255,0.15)] w-full max-w-4xl max-h-full overflow-hidden flex flex-col relative animate-in fade-in zoom-in-95 duration-200">
        
        {/* Header */}
        <div className="p-6 border-b border-white/10 flex justify-between items-start bg-black/50">
          <div>
            <div className="flex items-center gap-3 mb-2">
              <h2 className="text-3xl font-mono text-[#00f0ff] font-bold tracking-wider">{host.ip}</h2>
              {host.isRuin && (
                <span className="bg-gray-800 text-gray-300 text-xs px-2 py-1 rounded tracking-widest uppercase border border-gray-600">Offline Relic</span>
              )}
            </div>
            <div className="text-sm font-mono text-gray-400">
              HOSTNAME: <span className="text-white">{host.hostname?.replace(/\.(home|lan|local)$/i, '') || 'UNKNOWN'}</span>
            </div>
          </div>
          
          <button 
            onClick={onClose}
            className="text-gray-500 hover:text-white transition-colors p-2"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
          </button>
        </div>

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto p-6 flex flex-col md:flex-row gap-6 custom-scrollbar">
          
          {/* Left Column (Stats & Risk) */}
          <div className="w-full md:w-1/3 flex flex-col gap-6">
            
            <div className="bg-black/40 border border-white/5 rounded-lg p-5">
              <h3 className="text-xs font-bold text-gray-500 tracking-[0.2em] uppercase mb-4">Risk Profile</h3>
              <div className="flex items-end gap-3 mb-2">
                <span className={`text-4xl font-mono font-bold
                  ${host.riskBand === 'CRITICAL' ? 'text-red-500' : 
                    host.riskBand === 'HIGH' ? 'text-orange-500' :
                    host.riskBand === 'MEDIUM' ? 'text-yellow-500' :
                    host.riskBand === 'LOW' ? 'text-green-500' :
                    'text-gray-400'}
                `}>
                  {host.riskScore || 0}
                </span>
                <span className="text-sm text-gray-500 mb-1">/ 100</span>
              </div>
              <div className={`text-sm font-bold tracking-widest uppercase
                  ${host.riskBand === 'CRITICAL' ? 'text-red-500' : 
                    host.riskBand === 'HIGH' ? 'text-orange-500' :
                    host.riskBand === 'MEDIUM' ? 'text-yellow-500' :
                    host.riskBand === 'LOW' ? 'text-green-500' :
                    'text-gray-400'}
              `}>
                BAND: {host.riskBand || 'UNKNOWN'}
              </div>
            </div>

            <div className="bg-black/40 border border-white/5 rounded-lg p-5">
              <h3 className="text-xs font-bold text-gray-500 tracking-[0.2em] uppercase mb-4">System Identity</h3>
              <div className="space-y-4">
                <div>
                  <div className="text-[10px] text-gray-600 mb-1">OPERATING SYSTEM</div>
                  <div className="text-sm font-mono text-[#00f0ff]">{host.osGuess || 'UNKNOWN OS'}</div>
                </div>
                <div>
                  <div className="text-[10px] text-gray-600 mb-1">CONFIDENCE</div>
                  <div className="text-sm font-mono text-gray-300">{host.osAccuracy ? `${host.osAccuracy}%` : 'N/A'}</div>
                </div>
              </div>
            </div>
            
          </div>

          {/* Right Column (Ports & Vulns) */}
          <div className="w-full md:w-2/3 flex flex-col gap-6">
            
            {/* Risk Reasons Log */}
            <div className="bg-black/40 border border-white/5 rounded-lg p-5">
              <h3 className="text-xs font-bold text-gray-500 tracking-[0.2em] uppercase mb-4">Security Audit Log ({riskReasons.length})</h3>
              {riskReasons.length === 0 ? (
                <div className="text-sm text-gray-500 italic">No significant risks detected.</div>
              ) : (
                <div className="space-y-2">
                  {riskReasons.map((r: any, i: number) => (
                    <div key={i} className="flex items-start gap-3 bg-red-900/10 border border-red-500/20 p-3 rounded">
                      <span className="text-red-500 mt-0.5">⚠</span>
                      <div>
                        <div className="text-xs font-bold text-red-400 mb-1">{r.description}</div>
                        <div className="text-[10px] font-mono text-gray-400">Impact Score: +{r.points}</div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Ports List */}
            <div className="bg-black/40 border border-white/5 rounded-lg p-5">
              <h3 className="text-xs font-bold text-gray-500 tracking-[0.2em] uppercase mb-4">Open Ports ({ports.length})</h3>
              {ports.length === 0 ? (
                <div className="text-sm text-gray-500 italic">No open ports detected.</div>
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {ports.map((p: any, i: number) => (
                    <div key={i} className="flex items-center gap-3 bg-black/60 border border-white/10 p-2 rounded">
                      <div className="w-12 text-right font-mono text-[#00f0ff] font-bold text-sm">
                        {p.number}
                      </div>
                      <div className="flex-1">
                        <div className="text-xs text-white uppercase tracking-wider">{p.service || 'UNKNOWN'}</div>
                        <div className="text-[10px] font-mono text-gray-500">{p.state} • {p.protocol}</div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

          </div>

        </div>
      </div>
    </div>
  );
}

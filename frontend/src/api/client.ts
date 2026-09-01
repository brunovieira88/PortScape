const API_BASE = '/api';

export interface StartScanRequest {
  target?: string;
}

/**
 * Um erro que o backend explicou.
 *
 * <p>O backend responde em RFC 7807 com um {@code code} proprio -- INVALID_TARGET,
 * SCAN_QUEUE_FULL, NMAP_PRIVILEGE -- e um {@code detail} escrito para ser lido por
 * uma pessoa. Deitar isso fora e mostrar "Failed to start scan" e perder a unica
 * informacao util que ha: um utilizador que escreva um IP publico tem direito a saber
 * que o Portscape so faz scan de redes privadas, e nao a um erro generico.
 */
export class ApiError extends Error {
  readonly code: string | null;
  readonly status: number;

  constructor(message: string, code: string | null, status: number) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

/**
 * Le a explicacao da resposta, se lá vier alguma.
 *
 * <p>Nao pode falhar: um erro a tratar um erro deixava o utilizador sem nada. Se o
 * corpo nao for o JSON esperado -- um 502 do proxy, o backend em baixo -- fica o
 * fallback generico.
 */
async function failureOf(res: Response, fallback: string): Promise<ApiError> {
  try {
    const body = await res.json();
    const message = body?.detail || body?.message || fallback;
    return new ApiError(message, body?.code ?? null, res.status);
  } catch {
    return new ApiError(fallback, null, res.status);
  }
}

export async function startScan(target?: string, signal?: AbortSignal) {
  const req: StartScanRequest = {};
  if (target) req.target = target;

  const res = await fetch(`${API_BASE}/scans`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
    signal,
  });
  if (!res.ok) throw await failureOf(res, 'Não foi possível iniciar o scan.');
  return res.json(); // ScanResponse em PENDING ou RUNNING
}

export async function getScan(id: string, signal?: AbortSignal) {
  const res = await fetch(`${API_BASE}/scans/${id}`, { signal });
  if (!res.ok) throw await failureOf(res, 'Não foi possível carregar o scan.');
  return res.json();
}

export async function listScans(signal?: AbortSignal) {
  const res = await fetch(`${API_BASE}/scans`, { signal });
  if (!res.ok) throw await failureOf(res, 'Não foi possível carregar o histórico.');
  return res.json();
}

export async function deleteScan(id: string) {
  const res = await fetch(`${API_BASE}/scans/${id}`, { method: 'DELETE' });
  if (!res.ok) throw await failureOf(res, 'Não foi possível apagar o scan.');
}

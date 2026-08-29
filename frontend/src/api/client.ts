const API_BASE = '/api';

export interface StartScanRequest {
  target?: string;
}

export async function startScan(target?: string) {
  const req: StartScanRequest = {};
  if (target) req.target = target;
  
  const res = await fetch(`${API_BASE}/scans`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req)
  });
  if (!res.ok) throw new Error('Failed to start scan');
  return res.json(); // ScanResponse em QUEUED ou RUNNING
}

export async function getScan(id: string) {
  const res = await fetch(`${API_BASE}/scans/${id}`);
  if (!res.ok) throw new Error('Failed to get scan');
  return res.json();
}

export async function listScans() {
  const res = await fetch(`${API_BASE}/scans`);
  if (!res.ok) throw new Error('Failed to list scans');
  return res.json();
}

export async function deleteScan(id: string) {
  const res = await fetch(`${API_BASE}/scans/${id}`, {
    method: 'DELETE'
  });
  if (!res.ok) throw new Error('Failed to delete scan');
}

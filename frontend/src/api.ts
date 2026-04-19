import type { ApiResponse, IngestionResult } from './types';

const BASE = import.meta.env.VITE_API_BASE_URL ?? '';

export async function ingestPaper(arxivId: string): Promise<IngestionResult> {
  const res = await fetch(`${BASE}/api/ingest?arxivId=${encodeURIComponent(arxivId)}`, {
    method: 'POST',
  });
  const json: ApiResponse<IngestionResult> = await res.json();
  if (json.error) throw new Error(json.error);
  if (!json.data) throw new Error('Empty response');
  return json.data;
}

export function getWsUrl(): string {
  const base = import.meta.env.VITE_API_BASE_URL ?? '';
  if (base) {
    return base.replace(/^https?/, (p: string) => (p === 'https' ? 'wss' : 'ws')) + '/ws/chat';
  }
  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  return `${proto}://${location.host}/ws/chat`;
}

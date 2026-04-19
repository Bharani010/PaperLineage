import { useRef, useState, type KeyboardEvent, type MouseEvent } from 'react';

interface Props {
  onIngest: (arxivId: string) => void;
  loading: boolean;
  papersLoaded: number;
  view: 'graph' | 'leaderboard';
  onViewChange: (v: 'graph' | 'leaderboard') => void;
}

function parseArxivId(raw: string): string {
  const urlMatch = raw.match(/(?:arxiv\.org|alphaxiv\.org)\/(?:abs|pdf)\/([0-9]+\.[0-9]+)/i);
  if (urlMatch) return urlMatch[1];
  return raw.trim().replace(/^arxiv:/i, '').replace(/v\d+$/, '').trim();
}

export function TopBar({ onIngest, loading, papersLoaded, view, onViewChange }: Props) {
  const [input, setInput] = useState('');
  const btnRef = useRef<HTMLButtonElement>(null);

  const submit = () => {
    const id = parseArxivId(input);
    if (id) { onIngest(id); setInput(''); }
  };

  const onKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') submit();
  };

  const onBtnMove = (e: MouseEvent<HTMLButtonElement>) => {
    const btn = btnRef.current;
    if (!btn) return;
    const r = btn.getBoundingClientRect();
    btn.style.setProperty('--gx', `${((e.clientX - r.left) / r.width) * 100}%`);
    btn.style.setProperty('--gy', `${((e.clientY - r.top) / r.height) * 100}%`);
  };

  return (
    <header className="nav">
      <a href="/" className="nav-logo">
        Paper<span>Lineage</span>
      </a>

      <div className="nav-search">
        <svg className="nav-search-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="11" cy="11" r="8" /><path d="m21 21-4.35-4.35" />
        </svg>
        <input
          className="nav-input"
          type="text"
          placeholder="arXiv ID or URL (arxiv.org, alphaxiv.org)…"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={onKey}
          disabled={loading}
        />
      </div>

      <button
        ref={btnRef}
        className="nav-trace-btn"
        onClick={submit}
        onMouseMove={onBtnMove}
        disabled={loading || !input.trim()}
      >
        {loading ? (
          <>
            <Spinner />
            Ingesting…
          </>
        ) : (
          <>
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path d="M5 12h14M12 5l7 7-7 7" />
            </svg>
            Trace
          </>
        )}
      </button>

      <div className="nav-tabs">
        <button
          className={`nav-tab${view === 'graph' ? ' active' : ''}`}
          onClick={() => onViewChange('graph')}
        >
          Graph
        </button>
        <button
          className={`nav-tab${view === 'leaderboard' ? ' active' : ''}`}
          onClick={() => onViewChange('leaderboard')}
        >
          Rankings
        </button>
      </div>

      <div className="nav-status">
        <span className={`nav-dot${papersLoaded === 0 && !loading ? ' inactive' : loading ? ' loading' : ''}`} />
        <span className="nav-papers">
          {loading ? 'ingesting…' : papersLoaded > 0 ? `${papersLoaded} paper${papersLoaded > 1 ? 's' : ''}` : 'ready'}
        </span>
      </div>
    </header>
  );
}

function Spinner() {
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"
      style={{ animation: 'spin 0.7s linear infinite' }}>
      <path d="M21 12a9 9 0 1 1-6.219-8.56" />
    </svg>
  );
}

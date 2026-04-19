import { useState, type FormEvent, type KeyboardEvent } from 'react';

interface Props {
  onIngest: (arxivId: string) => void;
  loading: boolean;
  papersLoaded: number;
}

export function TopBar({ onIngest, loading, papersLoaded }: Props) {
  const [input, setInput] = useState('');

  const submit = () => {
    const id = input.trim().replace(/^(arxiv:|https?:\/\/arxiv\.org\/(abs|pdf)\/)/, '').replace(/v\d+$/, '').trim();
    if (id) { onIngest(id); setInput(''); }
  };

  const onKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') submit();
  };

  return (
    <header className="topbar">
      <a href="/" className="topbar-logo">
        <div className="logo-dot" />
        <span className="logo-text">Paper<span>Lineage</span></span>
      </a>

      <div className="topbar-search">
        <svg className="search-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="11" cy="11" r="8" /><path d="m21 21-4.35-4.35" />
        </svg>
        <input
          className="topbar-input"
          type="text"
          placeholder="Enter arXiv ID (e.g. 1706.03762) or URL…"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={onKey}
          disabled={loading}
        />
      </div>

      <button className="btn btn-primary" onClick={submit} disabled={loading || !input.trim()}>
        {loading ? (
          <>
            <Spinner />
            Ingesting…
          </>
        ) : (
          <>
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path d="M5 12h14M12 5l7 7-7 7" />
            </svg>
            Trace
          </>
        )}
      </button>

      <div className="topbar-status">
        <span
          className={`status-dot ${papersLoaded > 0 ? 'ok' : loading ? 'loading' : ''}`}
        />
        {papersLoaded > 0
          ? `${papersLoaded} paper${papersLoaded > 1 ? 's' : ''} loaded`
          : loading
          ? 'Ingesting…'
          : 'Ready'}
      </div>
    </header>
  );
}

function Spinner() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ animation: 'spin 0.7s linear infinite' }}>
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
      <path d="M21 12a9 9 0 1 1-6.219-8.56" />
    </svg>
  );
}

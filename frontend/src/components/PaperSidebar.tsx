import { useState } from 'react';
import type { ApiResponse, BenchmarkResult, GraphNode, HfModelInfo, IngestionResult, PwcData, RunGuide, ScoredRepo } from '../types';

interface Props {
  selected: GraphNode | null;
  papers: IngestionResult[];
  onShare: (arxivId: string) => void;
}

export function PaperSidebar({ selected, papers, onShare }: Props) {
  if (!selected) {
    return (
      <aside className="left-panel">
        <div className="panel-empty">
          <div className="panel-empty-icon">⬡</div>
          <h3>Select a node</h3>
          <p>Click any paper or repository in the graph to see its full analysis.</p>
          {papers.length === 0 && (
            <p style={{ marginTop: 8 }}>Paste an arXiv ID or URL above to get started.</p>
          )}
        </div>
      </aside>
    );
  }

  if (selected.type === 'paper') {
    const paper = papers.find((p) => p.arxivId === selected.id);
    return <PaperDetail node={selected} paper={paper ?? null} onShare={onShare} />;
  }

  const repo = papers.flatMap((p) => p.repos).find((r) => r.fullName === selected.id);
  const ownerPaper = papers.find((p) => p.repos.some((r) => r.fullName === selected.id));
  return <RepoDetail node={selected} repo={repo ?? null} paperTitle={ownerPaper?.title ?? ''} />;
}

// ── Paper detail ──────────────────────────────────────────────

function PaperDetail({ node, paper, onShare }: { node: GraphNode; paper: IngestionResult | null; onShare: (id: string) => void }) {
  return (
    <aside className="left-panel">
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
        <span className="paper-tag">Paper</span>
        <button className="share-btn" style={{ marginLeft: 'auto' }} onClick={() => onShare(node.id)} title="Copy shareable link">
          ↗ Share
        </button>
      </div>

      <p className="paper-title-serif">{node.label}</p>

      <div className="meta-section">
        <div>
          <div className="meta-label">arXiv</div>
          <div className="meta-value">
            <a href={`https://arxiv.org/abs/${node.id}`} target="_blank" rel="noopener noreferrer">{node.id}</a>
          </div>
        </div>
        {node.year ? (
          <div>
            <div className="meta-label">Published</div>
            <div className="meta-value plain">{node.year}</div>
          </div>
        ) : null}
        {node.authors && node.authors.length > 0 ? (
          <div>
            <div className="meta-label">Authors</div>
            <div className="author-chips" style={{ marginTop: 4 }}>
              {node.authors.slice(0, 5).map((a) => (
                <span key={a} className="author-chip">{a}</span>
              ))}
              {node.authors.length > 5 && (
                <span className="author-chip">+{node.authors.length - 5}</span>
              )}
            </div>
          </div>
        ) : null}
      </div>

      {paper ? (
        <div className="stats-grid">
          <div className="stat-cell">
            <span className="stat-num">{paper.forwardCitations}</span>
            <span className="stat-lbl">Cited by</span>
          </div>
          <div className="stat-cell">
            <span className="stat-num">{paper.backwardCitations}</span>
            <span className="stat-lbl">Refs</span>
          </div>
          <div className="stat-cell">
            <span className="stat-num">{paper.repos.length}</span>
            <span className="stat-lbl">Repos</span>
          </div>
        </div>
      ) : null}

      {paper?.pwcData ? <PwcSection pwc={paper.pwcData} /> : null}
      {paper?.hfModels && paper.hfModels.length > 0 ? <HfSection models={paper.hfModels} /> : null}

      {paper && paper.repos.length > 0 ? (
        <>
          <p className="section-divider" style={{ marginTop: 14 }}>Implementations — by runnability</p>
          <div className="impl-list">
            {paper.repos.map((repo, i) => (
              <RepoRow key={repo.fullName} repo={repo} rank={i + 1} />
            ))}
          </div>
        </>
      ) : null}
    </aside>
  );
}

// ── Papers With Code ──────────────────────────────────────────

function PwcSection({ pwc }: { pwc: PwcData }) {
  if (!pwc.found) {
    return (
      <div className="ext-section">
        <div className="ext-header">
          <span className="ext-title">Papers With Code</span>
          <span style={{ fontSize: 10, color: 'var(--text3)' }}>Not indexed</span>
        </div>
      </div>
    );
  }

  return (
    <div className="ext-section">
      <div className="ext-header">
        <span className="ext-title">Papers With Code</span>
        {pwc.pwcId ? (
          <a className="pwc-link" href={`https://paperswithcode.com/paper/${pwc.pwcId}`} target="_blank" rel="noopener noreferrer">
            View ↗
          </a>
        ) : null}
      </div>

      {pwc.tasks.length > 0 ? (
        <div style={{ marginBottom: 10 }}>
          <div className="meta-label" style={{ marginBottom: 4 }}>Tasks</div>
          <div className="chip-list">
            {pwc.tasks.map((t) => <span key={t} className="task-chip">{t}</span>)}
          </div>
        </div>
      ) : null}

      {pwc.methods.length > 0 ? (
        <div style={{ marginBottom: 10 }}>
          <div className="meta-label" style={{ marginBottom: 4 }}>Methods</div>
          <div className="chip-list">
            {pwc.methods.slice(0, 6).map((m) => <span key={m} className="method-chip">{m}</span>)}
          </div>
        </div>
      ) : null}

      {pwc.topResults.length > 0 ? (
        <div>
          <div className="meta-label" style={{ marginBottom: 6 }}>Benchmarks</div>
          <div style={{ overflowX: 'auto' }}>
            <table className="benchmark-table">
              <thead>
                <tr>
                  <th>Dataset</th><th>Metric</th><th>Value</th><th></th>
                </tr>
              </thead>
              <tbody>
                {pwc.topResults.map((r, i) => <BenchmarkRow key={i} result={r} />)}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function BenchmarkRow({ result }: { result: BenchmarkResult }) {
  return (
    <tr>
      <td style={{ maxWidth: 90, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{result.dataset}</td>
      <td>{result.metric}</td>
      <td style={{ fontWeight: 600, color: 'var(--text-1)' }}>{result.value}</td>
      <td>{result.isSota && <span className="sota-badge">SOTA</span>}</td>
    </tr>
  );
}

// ── HuggingFace ───────────────────────────────────────────────

function HfSection({ models }: { models: HfModelInfo[] }) {
  return (
    <div className="ext-section">
      <div className="ext-header">
        <span className="ext-title">🤗 HuggingFace Models</span>
        <span style={{ fontSize: 10, color: 'var(--accent3)' }}>{models.length} found</span>
      </div>
      <div className="model-list">
        {models.map((m) => (
          <a key={m.modelId} href={`https://huggingface.co/${m.modelId}`} target="_blank" rel="noopener noreferrer" className="model-item">
            <span className="model-name">{m.modelId}</span>
            <div className="model-meta">
              {m.pipelineTag ? <span className="model-badge">{m.pipelineTag}</span> : null}
              <span className="model-downloads">⬇ {formatCount(m.downloads)}</span>
            </div>
          </a>
        ))}
      </div>
    </div>
  );
}

// ── Repo rows (in paper detail) ───────────────────────────────

function RepoRow({ repo, rank }: { repo: ScoredRepo; rank: number }) {
  const badgeClass =
    repo.runnabilityLabel === 'Run it' ? 'badge-run' :
    repo.runnabilityLabel === 'Risky'  ? 'badge-risky' : 'badge-skip';
  return (
    <a href={repo.url} target="_blank" rel="noopener noreferrer" className="impl-item">
      <span className="impl-rank">#{rank}</span>
      <div style={{ flex: 1, overflow: 'hidden' }}>
        <div className="impl-name">{repo.fullName.split('/')[1]}</div>
        <div className="impl-sub">{repo.fullName.split('/')[0]}</div>
      </div>
      <span className={`impl-badge ${badgeClass}`}>{repo.runnabilityLabel}</span>
      <span className="impl-score">{repo.runnabilityScore}</span>
    </a>
  );
}

// ── Repo detail view ──────────────────────────────────────────

function RepoDetail({ node, repo, paperTitle }: { node: GraphNode; repo: ScoredRepo | null; paperTitle: string }) {
  const color = labelColor(node.runnabilityLabel ?? '');
  const [guide, setGuide] = useState<RunGuide | null>(null);
  const [guideLoading, setGuideLoading] = useState(false);
  const [guideError, setGuideError] = useState<string | null>(null);

  async function loadGuide() {
    if (!repo || guideLoading) return;
    setGuideLoading(true);
    setGuideError(null);
    try {
      const base = import.meta.env.VITE_API_BASE_URL ?? '';
      const params = new URLSearchParams({
        repoName: repo.fullName,
        repoUrl: repo.url,
        hasDocker: String(repo.hasDocker),
        hasCi: String(repo.hasCi),
        hasDeps: String(repo.hasDeps),
        score: String(repo.runnabilityScore),
        paperTitle,
      });
      const res = await fetch(`${base}/api/run-guide?${params}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json: ApiResponse<RunGuide> = await res.json();
      if (json.error) throw new Error(json.error);
      setGuide(json.data!);
    } catch (e) {
      setGuideError(e instanceof Error ? e.message : 'Failed to generate guide');
    } finally {
      setGuideLoading(false);
    }
  }

  return (
    <aside className="left-panel">
      <span className="repo-tag">Repository</span>
      <p className="paper-title-serif">{node.label}</p>

      {node.url ? (
        <div className="meta-section">
          <div>
            <div className="meta-label">GitHub</div>
            <div className="meta-value">
              <a href={node.url} target="_blank" rel="noopener noreferrer">{node.id}</a>
            </div>
          </div>
        </div>
      ) : null}

      {repo ? (
        <>
          <div className="repo-score-block">
            <div style={{ textAlign: 'center' }}>
              <div className="repo-score-num" style={{ color }}>{repo.runnabilityScore}</div>
              <div className="repo-score-sub">/ 100</div>
            </div>
            <div>
              <div className="repo-score-label" style={{ color }}>{repo.runnabilityLabel}</div>
              <div className="repo-score-stars">★ {repo.stars.toLocaleString()} stars</div>
            </div>
          </div>

          <div className="fidelity-bar-wrap">
            <div className="fidelity-bar-bg">
              <div className="fidelity-bar-fill"
                style={{ width: `${repo.runnabilityScore}%`, background: `linear-gradient(90deg,${color}99,${color})` }} />
            </div>
          </div>

          <div className="signal-badges">
            <SignalBadge ok={repo.hasCi}     label="CI"     />
            <SignalBadge ok={repo.hasDocker} label="Docker" />
            <SignalBadge ok={repo.hasDeps}   label="Deps"   />
            <SignalBadge
              ok={repo.daysSinceCommit <= 365}
              label={`${commitAge(repo.daysSinceCommit)} ago`}
            />
          </div>

          <div className="stats-grid" style={{ gridTemplateColumns: 'repeat(2, 1fr)' }}>
            <div className="stat-cell">
              <span className="stat-num">{repo.stars.toLocaleString()}</span>
              <span className="stat-lbl">Stars</span>
            </div>
            <div className="stat-cell">
              <span className="stat-num">{commitAge(repo.daysSinceCommit)}</span>
              <span className="stat-lbl">Last commit</span>
            </div>
          </div>

          {!guide ? (
            <button className="run-guide-btn" onClick={loadGuide} disabled={guideLoading}>
              {guideLoading ? '⏳ Generating guide…' : '▶ How to Run'}
            </button>
          ) : null}
          {guideError ? (
            <p style={{ fontSize: 11, color: 'var(--red)', marginTop: 8 }}>{guideError}</p>
          ) : null}
          {guide ? <RunGuidePanel guide={guide} /> : null}
        </>
      ) : null}
    </aside>
  );
}

// ── Run Guide panel ───────────────────────────────────────────

function RunGuidePanel({ guide }: { guide: RunGuide }) {
  const diffColor =
    guide.difficulty === 'Easy' ? 'var(--accent3)' :
    guide.difficulty === 'Hard' ? 'var(--red)' : 'var(--accent)';

  return (
    <div className="run-guide-panel">
      <div className="run-guide-header">
        <span style={{ fontWeight: 700, fontSize: 13 }}>How to Run</span>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <span style={{ fontSize: 11, color: diffColor, fontWeight: 600 }}>{guide.difficulty}</span>
          <span style={{ fontSize: 11, color: 'var(--text3)' }}>~{guide.estimatedTime}</span>
        </div>
      </div>

      {guide.prerequisites.length > 0 ? (
        <div className="run-guide-section">
          <p className="run-guide-section-title">Prerequisites</p>
          <ul className="run-guide-list">
            {guide.prerequisites.map((p, i) => <li key={i}>{p}</li>)}
          </ul>
        </div>
      ) : null}

      {guide.steps.length > 0 ? (
        <div className="run-guide-section">
          <p className="run-guide-section-title">Steps</p>
          {guide.steps.map((step, i) => (
            <div key={i} className="run-guide-step">
              <div className="run-guide-step-num">{i + 1}</div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div className="run-guide-step-title">{step.title}</div>
                {step.command ? <code className="run-guide-command">{step.command}</code> : null}
                {step.notes ? <p className="run-guide-notes">{step.notes}</p> : null}
              </div>
            </div>
          ))}
        </div>
      ) : null}

      {guide.commonIssues.length > 0 ? (
        <div className="run-guide-section">
          <p className="run-guide-section-title">Common Issues</p>
          <ul className="run-guide-list run-guide-issues">
            {guide.commonIssues.map((issue, i) => <li key={i}>{issue}</li>)}
          </ul>
        </div>
      ) : null}
    </div>
  );
}

function SignalBadge({ ok, label }: { ok: boolean; label: string }) {
  return (
    <span className="signal-badge" style={{
      background: ok ? 'rgba(110,203,168,0.1)' : 'rgba(71,85,105,0.15)',
      color: ok ? 'var(--accent3)' : 'var(--text3)',
      border: `1px solid ${ok ? 'rgba(110,203,168,0.25)' : 'rgba(71,85,105,0.2)'}`,
    }}>
      {ok ? '✓' : '✗'} {label}
    </span>
  );
}

function labelColor(label: string) {
  if (label === 'Run it') return '#6ecba8';
  if (label === 'Risky')  return '#c8a96e';
  return '#e07060';
}

function commitAge(days: number): string {
  if (days <= 30)  return `${days}d`;
  if (days <= 365) return `${Math.round(days / 30)}mo`;
  return `${(days / 365).toFixed(1)}yr`;
}

function formatCount(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000)     return `${(n / 1_000).toFixed(1)}k`;
  return String(n);
}

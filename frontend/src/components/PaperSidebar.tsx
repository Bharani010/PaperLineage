import type { BenchmarkResult, GraphNode, HfModelInfo, IngestionResult, PwcData, ScoredRepo } from '../types';

interface Props {
  selected: GraphNode | null;
  papers: IngestionResult[];
}

export function PaperSidebar({ selected, papers }: Props) {
  if (!selected) {
    return (
      <aside className="sidebar">
        <div className="sidebar-empty">
          <div className="sidebar-empty-icon">🔬</div>
          <h3>Select a node</h3>
          <p>Click any paper or repository node to see its full analysis.</p>
          {papers.length === 0 && (
            <p style={{ marginTop: 8 }}>Enter an arXiv ID above to get started.</p>
          )}
        </div>
      </aside>
    );
  }

  if (selected.type === 'paper') {
    const paper = papers.find((p) => p.arxivId === selected.id);
    return <PaperDetail node={selected} paper={paper ?? null} />;
  }

  const repo = papers.flatMap((p) => p.repos).find((r) => r.fullName === selected.id);
  return <RepoDetail node={selected} repo={repo ?? null} />;
}

// ── Paper detail view ─────────────────────────────────────────

function PaperDetail({ node, paper }: { node: GraphNode; paper: IngestionResult | null }) {
  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, flex: 1 }}>
          <span className="sidebar-type-badge badge-paper">Paper</span>
          <p className="sidebar-title">{node.label}</p>
        </div>
      </div>

      <div className="sidebar-body">
        {/* Core metadata */}
        <div className="info-row">
          <span className="info-label">arXiv</span>
          <span className="info-value">
            <a href={`https://arxiv.org/abs/${node.id}`} target="_blank" rel="noopener noreferrer">
              {node.id}
            </a>
          </span>
        </div>

        {node.year && (
          <div className="info-row">
            <span className="info-label">Published</span>
            <span className="info-value">{node.year}</span>
          </div>
        )}

        {node.authors && node.authors.length > 0 && (
          <div className="info-row">
            <span className="info-label">Authors</span>
            <div className="authors-list">
              {node.authors.slice(0, 5).map((a) => (
                <span key={a} className="author-chip">{a}</span>
              ))}
              {node.authors.length > 5 && (
                <span className="author-chip">+{node.authors.length - 5}</span>
              )}
            </div>
          </div>
        )}

        {paper && (
          <div className="stats-row">
            <div className="stat-card">
              <div className="stat-value">{paper.forwardCitations}</div>
              <div className="stat-label">Cited by</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">{paper.backwardCitations}</div>
              <div className="stat-label">References</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">{paper.repos.length}</div>
              <div className="stat-label">Repos</div>
            </div>
          </div>
        )}

        {/* Papers With Code section */}
        {paper?.pwcData && <PwcSection pwc={paper.pwcData} />}

        {/* HuggingFace section */}
        {paper?.hfModels && paper.hfModels.length > 0 && (
          <HfSection models={paper.hfModels} />
        )}

        {/* Implementation leaderboard */}
        {paper && paper.repos.length > 0 && (
          <div className="repos-section">
            <p className="repos-section-title">Implementations — by runnability</p>
            {paper.repos.map((repo, i) => (
              <RepoRow key={repo.fullName} repo={repo} rank={i + 1} />
            ))}
          </div>
        )}
      </div>
    </aside>
  );
}

// ── Papers With Code section ──────────────────────────────────

function PwcSection({ pwc }: { pwc: PwcData }) {
  if (!pwc.found) {
    return (
      <div className="ext-section">
        <div className="ext-section-header">
          <span className="ext-section-title">Papers With Code</span>
          <span style={{ fontSize: 10, color: 'var(--text-3)' }}>Not indexed</span>
        </div>
      </div>
    );
  }

  return (
    <div className="ext-section">
      <div className="ext-section-header">
        <span className="ext-section-title">Papers With Code</span>
        {pwc.pwcId && (
          <a
            className="pwc-link"
            href={`https://paperswithcode.com/paper/${pwc.pwcId}`}
            target="_blank"
            rel="noopener noreferrer"
          >
            View ↗
          </a>
        )}
      </div>

      {pwc.tasks.length > 0 && (
        <div className="info-row" style={{ marginBottom: 10 }}>
          <span className="info-label">Tasks</span>
          <div className="authors-list" style={{ marginTop: 4 }}>
            {pwc.tasks.map((t) => (
              <span key={t} className="task-chip">{t}</span>
            ))}
          </div>
        </div>
      )}

      {pwc.methods.length > 0 && (
        <div className="info-row" style={{ marginBottom: 10 }}>
          <span className="info-label">Methods</span>
          <div className="authors-list" style={{ marginTop: 4 }}>
            {pwc.methods.slice(0, 6).map((m) => (
              <span key={m} className="method-chip">{m}</span>
            ))}
          </div>
        </div>
      )}

      {pwc.topResults.length > 0 && (
        <div className="info-row">
          <span className="info-label">Benchmarks</span>
          <div style={{ marginTop: 6, overflowX: 'auto' }}>
            <table className="benchmark-table">
              <thead>
                <tr>
                  <th>Dataset</th>
                  <th>Metric</th>
                  <th>Value</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {pwc.topResults.map((r, i) => (
                  <BenchmarkRow key={i} result={r} />
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

function BenchmarkRow({ result }: { result: BenchmarkResult }) {
  return (
    <tr>
      <td style={{ maxWidth: 90, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
        {result.dataset}
      </td>
      <td>{result.metric}</td>
      <td style={{ fontWeight: 600, color: 'var(--text-1)' }}>{result.value}</td>
      <td>{result.isSota && <span className="sota-badge">SOTA</span>}</td>
    </tr>
  );
}

// ── HuggingFace section ───────────────────────────────────────

function HfSection({ models }: { models: HfModelInfo[] }) {
  return (
    <div className="ext-section">
      <div className="ext-section-header">
        <span className="ext-section-title">🤗 HuggingFace Models</span>
        <span style={{ fontSize: 10, color: 'var(--emerald)' }}>{models.length} found</span>
      </div>
      {models.map((m) => (
        <a
          key={m.modelId}
          href={`https://huggingface.co/${m.modelId}`}
          target="_blank"
          rel="noopener noreferrer"
          className="hf-model-item"
        >
          <div style={{ flex: 1, overflow: 'hidden' }}>
            <div className="hf-model-name">{m.modelId}</div>
          </div>
          {m.pipelineTag && <span className="hf-pipeline-tag">{m.pipelineTag}</span>}
          <span className="hf-downloads">⬇ {formatCount(m.downloads)}</span>
        </a>
      ))}
    </div>
  );
}

// ── Repo sections ─────────────────────────────────────────────

function RepoRow({ repo, rank }: { repo: ScoredRepo; rank: number }) {
  const color = labelColor(repo.runnabilityLabel);
  return (
    <a href={repo.url} target="_blank" rel="noopener noreferrer" className="repo-item">
      <span style={{ fontSize: 10, color: 'var(--text-3)', width: 14, flexShrink: 0 }}>#{rank}</span>
      <div style={{ flex: 1, overflow: 'hidden' }}>
        <div className="repo-item-name">{repo.fullName.split('/')[1]}</div>
        <div style={{ fontSize: 10, color: 'var(--text-3)', marginTop: 1 }}>
          {repo.fullName.split('/')[0]}
        </div>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 2, flexShrink: 0 }}>
        <span style={{ fontSize: 12, fontWeight: 700, color }}>{repo.runnabilityScore}</span>
        <span style={{ fontSize: 9, color, fontWeight: 600 }}>{repo.runnabilityLabel}</span>
      </div>
    </a>
  );
}

function RepoDetail({ node, repo }: { node: GraphNode; repo: ScoredRepo | null }) {
  const color = labelColor(node.runnabilityLabel ?? '');
  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, flex: 1 }}>
          <span className="sidebar-type-badge badge-repo">Repository</span>
          <p className="sidebar-title">{node.label}</p>
        </div>
      </div>
      <div className="sidebar-body">
        {node.url && (
          <div className="info-row">
            <span className="info-label">GitHub</span>
            <span className="info-value">
              <a href={node.url} target="_blank" rel="noopener noreferrer">{node.id}</a>
            </span>
          </div>
        )}
        {repo && (
          <>
            <div style={{
              display: 'flex', alignItems: 'center', gap: 12,
              background: 'var(--bg-surface2)', border: '1px solid var(--border)',
              borderRadius: 'var(--radius-md)', padding: '14px 16px',
            }}>
              <div style={{ textAlign: 'center' }}>
                <div style={{ fontSize: 32, fontWeight: 800, color, lineHeight: 1 }}>
                  {repo.runnabilityScore}
                </div>
                <div style={{ fontSize: 10, color: 'var(--text-3)', marginTop: 2 }}>/ 100</div>
              </div>
              <div>
                <div style={{ fontSize: 14, fontWeight: 700, color }}>{repo.runnabilityLabel}</div>
                <div style={{ fontSize: 11, color: 'var(--text-3)', marginTop: 3 }}>
                  ★ {repo.stars.toLocaleString()} stars
                </div>
              </div>
            </div>

            <div className="fidelity-bar-wrap">
              <div className="fidelity-bar-bg">
                <div className="fidelity-bar-fill"
                  style={{ width: `${repo.runnabilityScore}%`, background: `linear-gradient(90deg,${color}99,${color})` }} />
              </div>
            </div>

            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              <SignalBadge ok={repo.hasCi}     label="CI"     />
              <SignalBadge ok={repo.hasDocker} label="Docker" />
              <SignalBadge ok={repo.hasDeps}   label="Deps"   />
              <SignalBadge
                ok={repo.daysSinceCommit <= 365}
                label={`${commitAge(repo.daysSinceCommit)} ago`}
              />
            </div>

            <div className="stats-row">
              <div className="stat-card">
                <div className="stat-value">{repo.stars.toLocaleString()}</div>
                <div className="stat-label">Stars</div>
              </div>
              <div className="stat-card">
                <div className="stat-value">{commitAge(repo.daysSinceCommit)}</div>
                <div className="stat-label">Last commit</div>
              </div>
            </div>
          </>
        )}
      </div>
    </aside>
  );
}

function SignalBadge({ ok, label }: { ok: boolean; label: string }) {
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 4,
      padding: '3px 8px', borderRadius: 20, fontSize: 11, fontWeight: 500,
      background: ok ? 'rgba(16,185,129,0.1)' : 'rgba(71,85,105,0.2)',
      color: ok ? 'var(--emerald)' : 'var(--text-3)',
      border: `1px solid ${ok ? 'rgba(16,185,129,0.25)' : 'rgba(71,85,105,0.2)'}`,
    }}>
      {ok ? '✓' : '✗'} {label}
    </span>
  );
}

function labelColor(label: string) {
  if (label === 'Run it') return '#10b981';
  if (label === 'Risky')  return '#f59e0b';
  return '#ef4444';
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

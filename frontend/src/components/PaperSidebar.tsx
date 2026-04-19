import type { GraphNode, IngestionResult, ScoredRepo } from '../types';

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
          <p>Click any paper or repository node to see its details.</p>
          {papers.length === 0 && (
            <p style={{ marginTop: 8 }}>
              Enter an arXiv ID above to analyse a paper&apos;s implementations.
            </p>
          )}
        </div>
      </aside>
    );
  }

  if (selected.type === 'paper') {
    const paper = papers.find((p) => p.arxivId === selected.id);
    return (
      <aside className="sidebar">
        <div className="sidebar-header">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, flex: 1 }}>
            <span className="sidebar-type-badge badge-paper">Paper</span>
            <p className="sidebar-title">{selected.label}</p>
          </div>
        </div>

        <div className="sidebar-body">
          <div className="info-row">
            <span className="info-label">arXiv ID</span>
            <span className="info-value">
              <a href={`https://arxiv.org/abs/${selected.id}`} target="_blank" rel="noopener noreferrer">
                {selected.id}
              </a>
            </span>
          </div>

          {selected.year && (
            <div className="info-row">
              <span className="info-label">Published</span>
              <span className="info-value">{selected.year}</span>
            </div>
          )}

          {selected.authors && selected.authors.length > 0 && (
            <div className="info-row">
              <span className="info-label">Authors</span>
              <div className="authors-list">
                {selected.authors.slice(0, 5).map((a) => (
                  <span key={a} className="author-chip">{a}</span>
                ))}
                {selected.authors.length > 5 && (
                  <span className="author-chip">+{selected.authors.length - 5}</span>
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

          {paper && paper.repos.length > 0 && (
            <div className="repos-section">
              <p className="repos-section-title">Implementations — ranked by runnability</p>
              {paper.repos.map((repo, i) => (
                <RepoRow key={repo.fullName} repo={repo} rank={i + 1} />
              ))}
            </div>
          )}
        </div>
      </aside>
    );
  }

  // Repo node
  const repoFullName = selected.id;
  const paper = papers.find((p) => p.repos.some((r) => r.fullName === repoFullName));
  const repo = paper?.repos.find((r) => r.fullName === repoFullName);

  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, flex: 1 }}>
          <span className="sidebar-type-badge badge-repo">Repository</span>
          <p className="sidebar-title">{selected.label}</p>
        </div>
      </div>

      <div className="sidebar-body">
        {selected.url && (
          <div className="info-row">
            <span className="info-label">GitHub</span>
            <span className="info-value">
              <a href={selected.url} target="_blank" rel="noopener noreferrer">{repoFullName}</a>
            </span>
          </div>
        )}

        {repo && <RepoScoreBreakdown repo={repo} />}
      </div>
    </aside>
  );
}

// ── Repo row in paper's implementation list ──────────────────

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

// ── Full score breakdown for selected repo node ───────────────

function RepoScoreBreakdown({ repo }: { repo: ScoredRepo }) {
  const color = labelColor(repo.runnabilityLabel);

  return (
    <>
      {/* Big score */}
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

      {/* Score bar */}
      <div className="fidelity-bar-wrap">
        <div className="fidelity-bar-bg">
          <div
            className="fidelity-bar-fill"
            style={{
              width: `${repo.runnabilityScore}%`,
              background: `linear-gradient(90deg, ${color}99, ${color})`,
            }}
          />
        </div>
      </div>

      {/* Signal badges */}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
        <SignalBadge ok={repo.hasCi}     label="CI"      />
        <SignalBadge ok={repo.hasDocker} label="Docker"  />
        <SignalBadge ok={repo.hasDeps}   label="Deps"    />
        <SignalBadge
          ok={repo.daysSinceCommit <= 365}
          label={`${commitAge(repo.daysSinceCommit)} ago`}
        />
      </div>

      {/* Stats */}
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
  if (label === 'Run it')       return '#10b981';
  if (label === 'Risky')        return '#f59e0b';
  return '#ef4444';
}

function commitAge(days: number): string {
  if (days <= 30)  return `${days}d`;
  if (days <= 365) return `${Math.round(days / 30)}mo`;
  return `${Math.round(days / 365)}yr`;
}

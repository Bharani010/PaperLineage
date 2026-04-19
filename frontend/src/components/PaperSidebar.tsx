import type { GraphNode, IngestionResult } from '../types';

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
          <p>Click any paper or repository node in the graph to see details here.</p>
          {papers.length === 0 && (
            <p style={{ marginTop: 8 }}>
              Start by entering an arXiv ID in the search bar to trace a paper&apos;s lineage.
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
            <span className="sidebar-type-badge badge-paper">
              <svg width="9" height="9" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="12" r="10"/></svg>
              Paper
            </span>
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
                {selected.authors.slice(0, 6).map((a) => (
                  <span key={a} className="author-chip">{a}</span>
                ))}
                {selected.authors.length > 6 && (
                  <span className="author-chip">+{selected.authors.length - 6} more</span>
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
              <p className="repos-section-title">Implementations</p>
              {paper.repos.map((repo) => (
                <a
                  key={repo.fullName}
                  href={repo.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="repo-item"
                >
                  <div className="repo-item-dot" />
                  <span className="repo-item-name">{repo.fullName}</span>
                  <span className="repo-item-stars">★ {repo.stars.toLocaleString()}</span>
                </a>
              ))}
            </div>
          )}
        </div>
      </aside>
    );
  }

  // Repo node
  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, flex: 1 }}>
          <span className="sidebar-type-badge badge-repo">
            <svg width="9" height="9" viewBox="0 0 24 24" fill="currentColor"><rect x="3" y="3" width="18" height="18" rx="2"/></svg>
            Repository
          </span>
          <p className="sidebar-title">{selected.label}</p>
        </div>
      </div>

      <div className="sidebar-body">
        {selected.url && (
          <div className="info-row">
            <span className="info-label">GitHub</span>
            <span className="info-value">
              <a href={selected.url} target="_blank" rel="noopener noreferrer">
                {selected.id}
              </a>
            </span>
          </div>
        )}

        {selected.stars !== undefined && (
          <div className="stats-row">
            <div className="stat-card">
              <div className="stat-value">{selected.stars.toLocaleString()}</div>
              <div className="stat-label">Stars</div>
            </div>
            {selected.fidelityScore !== undefined && (
              <div className="stat-card">
                <div className="stat-value">{(selected.fidelityScore * 100).toFixed(0)}%</div>
                <div className="stat-label">Fidelity</div>
              </div>
            )}
          </div>
        )}

        {selected.fidelityScore !== undefined && (
          <div className="fidelity-bar-wrap">
            <div className="fidelity-bar-row">
              <span className="info-label">Implementation Fidelity</span>
              <span style={{ fontSize: 11, color: 'var(--emerald)' }}>
                {(selected.fidelityScore * 100).toFixed(1)}%
              </span>
            </div>
            <div className="fidelity-bar-bg">
              <div
                className="fidelity-bar-fill"
                style={{ width: `${(selected.fidelityScore * 100).toFixed(1)}%` }}
              />
            </div>
          </div>
        )}
      </div>
    </aside>
  );
}

import type { IngestionResult, ScoredRepo } from '../types';

interface LeaderboardRow {
  paper: IngestionResult;
  bestRepo: ScoredRepo | null;
  bestScore: number;
}

interface Props {
  papers: IngestionResult[];
  onSelectPaper: (arxivId: string) => void;
  onShare: (arxivId: string) => void;
}

function buildRows(papers: IngestionResult[]): LeaderboardRow[] {
  return papers
    .map((paper) => {
      const bestRepo = paper.repos.reduce<ScoredRepo | null>(
        (best, r) => (!best || r.runnabilityScore > best.runnabilityScore ? r : best),
        null
      );
      return { paper, bestRepo, bestScore: bestRepo?.runnabilityScore ?? 0 };
    })
    .sort((a, b) => b.bestScore - a.bestScore);
}

export function LeaderboardView({ papers, onSelectPaper, onShare }: Props) {
  if (papers.length === 0) {
    return (
      <div className="lb-empty">
        <div className="lb-empty-icon">📊</div>
        <p>No papers ingested yet. Enter an arXiv ID above to get started.</p>
      </div>
    );
  }

  const rows = buildRows(papers);

  return (
    <div className="lb-wrap">
      <div className="lb-header-row">
        <h2 className="lb-title">Implementation Leaderboard</h2>
        <span className="lb-subtitle">{papers.length} paper{papers.length !== 1 ? 's' : ''} — ranked by best runnability score</span>
      </div>

      <div className="lb-table-wrap">
        <table className="lb-table">
          <thead>
            <tr>
              <th style={{ width: 36 }}>#</th>
              <th>Paper</th>
              <th style={{ width: 56 }}>Year</th>
              <th>Best Repo</th>
              <th style={{ width: 100 }}>Score</th>
              <th style={{ width: 96 }}>Signals</th>
              <th style={{ width: 72 }}>Stars</th>
              <th style={{ width: 72 }}>Age</th>
              <th style={{ width: 80 }}></th>
            </tr>
          </thead>
          <tbody>
            {rows.map(({ paper, bestRepo, bestScore }, i) => (
              <LeaderboardRow
                key={paper.arxivId}
                rank={i + 1}
                paper={paper}
                bestRepo={bestRepo}
                bestScore={bestScore}
                onOpen={() => onSelectPaper(paper.arxivId)}
                onShare={() => onShare(paper.arxivId)}
              />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

interface RowProps {
  rank: number;
  paper: IngestionResult;
  bestRepo: ScoredRepo | null;
  bestScore: number;
  onOpen: () => void;
  onShare: () => void;
}

function LeaderboardRow({ rank, paper, bestRepo, bestScore, onOpen, onShare }: RowProps) {
  const color = scoreColor(bestScore);

  return (
    <tr className="lb-row" onClick={onOpen}>
      <td className="lb-rank">{rank}</td>

      <td className="lb-paper-cell">
        <div className="lb-paper-title">{paper.title}</div>
        <div className="lb-paper-meta">
          {paper.authors.slice(0, 3).join(', ')}
          {paper.authors.length > 3 && ' et al.'}
          {' · '}
          <a
            href={`https://arxiv.org/abs/${paper.arxivId}`}
            target="_blank"
            rel="noopener noreferrer"
            onClick={(e) => e.stopPropagation()}
            className="lb-arxiv-link"
          >
            {paper.arxivId}
          </a>
        </div>
      </td>

      <td className="lb-year">{paper.year}</td>

      <td className="lb-repo-cell">
        {bestRepo ? (
          <>
            <div className="lb-repo-name">{bestRepo.fullName.split('/')[1]}</div>
            <div className="lb-repo-owner">{bestRepo.fullName.split('/')[0]}</div>
          </>
        ) : (
          <span style={{ color: 'var(--text-3)', fontSize: 11 }}>—</span>
        )}
      </td>

      <td>
        {bestRepo ? (
          <div className="lb-score-cell">
            <span className="lb-score-num" style={{ color }}>{bestScore}</span>
            <div className="lb-score-bar-bg">
              <div className="lb-score-bar-fill" style={{ width: `${bestScore}%`, background: color }} />
            </div>
            <span className="lb-score-label" style={{ color }}>{bestRepo.runnabilityLabel}</span>
          </div>
        ) : (
          <span style={{ color: 'var(--text-3)', fontSize: 11 }}>—</span>
        )}
      </td>

      <td>
        {bestRepo ? (
          <div className="lb-signals">
            <SignalDot ok={bestRepo.hasCi}     title="CI" />
            <SignalDot ok={bestRepo.hasDocker} title="Docker" />
            <SignalDot ok={bestRepo.hasDeps}   title="Deps" />
          </div>
        ) : null}
      </td>

      <td className="lb-num">
        {bestRepo ? bestRepo.stars.toLocaleString() : '—'}
      </td>

      <td className="lb-num">
        {bestRepo ? commitAge(bestRepo.daysSinceCommit) : '—'}
      </td>

      <td>
        <div className="lb-actions" onClick={(e) => e.stopPropagation()}>
          <button className="lb-btn lb-btn-open" onClick={onOpen} title="View in graph">
            ⬡
          </button>
          <button className="lb-btn lb-btn-share" onClick={onShare} title="Copy shareable link">
            ↗
          </button>
        </div>
      </td>
    </tr>
  );
}

function SignalDot({ ok, title }: { ok: boolean; title: string }) {
  return (
    <span
      className="lb-signal-dot"
      title={title}
      style={{ background: ok ? 'var(--emerald)' : 'var(--bg-hover)' }}
    />
  );
}

function scoreColor(score: number): string {
  if (score >= 71) return '#10b981';
  if (score >= 41) return '#f59e0b';
  return '#ef4444';
}

function commitAge(days: number): string {
  if (days <= 30) return `${days}d`;
  if (days <= 365) return `${Math.round(days / 30)}mo`;
  return `${(days / 365).toFixed(1)}yr`;
}

import { useCallback, useState } from 'react';
import { ingestPaper } from './api';
import { ChatPanel } from './components/ChatPanel';
import { ForceGraph } from './components/ForceGraph';
import { PaperSidebar } from './components/PaperSidebar';
import { TopBar } from './components/TopBar';
import { useChat } from './hooks/useChat';
import type { GraphLink, GraphNode, IngestionResult } from './types';

interface Toast { id: string; type: 'error' | 'success'; text: string; }

function buildGraph(papers: IngestionResult[]): { nodes: GraphNode[]; links: GraphLink[] } {
  const nodes: GraphNode[] = [];
  const links: GraphLink[] = [];
  const seen = new Set<string>();

  papers.forEach((paper, idx) => {
    if (!seen.has(paper.arxivId)) {
      seen.add(paper.arxivId);
      nodes.push({
        id: paper.arxivId,
        type: 'paper',
        label: paper.title,
        year: paper.year,
        authors: paper.authors,
        forwardCitations: paper.forwardCitations,
        backwardCitations: paper.backwardCitations,
        isRoot: idx === 0,
      });
    }

    paper.repos.forEach((repo) => {
      const repoId = repo.fullName;
      if (!seen.has(repoId)) {
        seen.add(repoId);
        nodes.push({
          id: repoId,
          type: 'repo',
          label: repoId.split('/')[1] ?? repoId,
          stars: repo.stars,
          fidelityScore: repo.fidelityScore,
          url: repo.url,
        });
      }
      links.push({ source: paper.arxivId, target: repoId, type: 'implements' });
    });
  });

  return { nodes, links };
}

export default function App() {
  const [papers, setPapers] = useState<IngestionResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const { messages, status, sendMessage, clearMessages, reconnect } = useChat();

  const addToast = (type: Toast['type'], text: string) => {
    const id = crypto.randomUUID();
    setToasts((t) => [...t, { id, type, text }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 4000);
  };

  const handleIngest = useCallback(async (arxivId: string) => {
    setLoading(true);
    try {
      const result = await ingestPaper(arxivId);
      setPapers((prev) => {
        if (prev.find((p) => p.arxivId === result.arxivId)) return prev;
        return [result, ...prev];
      });
      setSelectedId(result.arxivId);
      addToast('success', `"${result.title.slice(0, 50)}…" ingested`);
    } catch (e) {
      addToast('error', e instanceof Error ? e.message : 'Ingestion failed');
    } finally {
      setLoading(false);
    }
  }, []);

  const { nodes, links } = buildGraph(papers);

  const selectedNode = nodes.find((n) => n.id === selectedId) ?? null;

  const handleNodeClick = useCallback((node: GraphNode) => {
    setSelectedId((prev) => (prev === node.id ? null : node.id));
  }, []);

  return (
    <div className="app">
      <TopBar onIngest={handleIngest} loading={loading} papersLoaded={papers.length} />

      <div className="app-body">
        <PaperSidebar selected={selectedNode} papers={papers} />

        <ForceGraph
          nodes={nodes}
          links={links}
          selectedId={selectedId}
          onNodeClick={handleNodeClick}
        />

        <ChatPanel
          messages={messages}
          status={status}
          onSend={sendMessage}
          onClear={clearMessages}
          onReconnect={reconnect}
        />
      </div>

      {toasts.map((t) => (
        <div key={t.id} className="toast-wrap">
          <div className={`toast ${t.type}`}>{t.text}</div>
        </div>
      ))}
    </div>
  );
}

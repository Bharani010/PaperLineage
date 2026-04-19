import type { SimulationNodeDatum, SimulationLinkDatum } from 'd3';

export interface ApiResponse<T> {
  data: T | null;
  error: string | null;
  meta: Record<string, unknown> | null;
}

export interface ScoredRepo {
  fullName: string;
  url: string;
  stars: number;
  runnabilityScore: number;
  runnabilityLabel: 'Run it' | 'Risky' | "Don't bother";
  hasCi: boolean;
  hasDocker: boolean;
  hasDeps: boolean;
  daysSinceCommit: number;
}

export interface IngestionResult {
  arxivId: string;
  title: string;
  year: number;
  authors: string[];
  forwardCitations: number;
  backwardCitations: number;
  repos: ScoredRepo[];
}

export interface GraphNode extends SimulationNodeDatum {
  id: string;
  type: 'paper' | 'repo';
  label: string;
  year?: number;
  forwardCitations?: number;
  backwardCitations?: number;
  stars?: number;
  runnabilityScore?: number;
  runnabilityLabel?: string;
  hasCi?: boolean;
  hasDocker?: boolean;
  hasDeps?: boolean;
  url?: string;
  authors?: string[];
  isRoot?: boolean;
}

export interface GraphLink extends SimulationLinkDatum<GraphNode> {
  type: 'implements';
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  sources?: string[];
  streaming?: boolean;
}

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
  fidelityScore: number;
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
  sublabel?: string;
  year?: number;
  forwardCitations?: number;
  backwardCitations?: number;
  stars?: number;
  fidelityScore?: number;
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

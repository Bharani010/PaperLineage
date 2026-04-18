# PaperLineage

Research tool that traces academic paper citation lineage, maps GitHub implementations, and scores implementation fidelity — using Spring Boot, Neo4j, pgvector, and a single Claude API call only for chat.

## Hard constraints
- Zero cost. No paid APIs beyond Claude chat calls. No GPU. Deploys to Railway free tier.
- No LLM during ingestion. Pipeline is deterministic Java services only.
- Java 21 + Spring Boot 3.3.x. Maven. No Kotlin, no Quarkus, no Gradle.
- Single Claude API call per chat message. Model: claude-haiku-4-5. Never call Claude during ingestion.
- No LangChain4j. No Ollama. No Docker Compose locally.

## Architecture
Frontend  →  React 18 + Vite + TypeScript + D3.js  →  Vercel
Backend   →  Spring Boot 3.3                        →  Railway
Graph DB  →  Neo4j AuraDB free tier
Vector DB →  pgvector on Supabase free tier
Embed API →  HuggingFace Inference API (all-MiniLM-L6-v2, free)
Chat LLM  →  Claude Haiku (only on user chat messages)
Data APIs →  arXiv API, Semantic Scholar API, GitHub API (all free)

## Package structure
com.paperlineage.ingestion   — IngestionService, CitationFetcher, RepoSearcher, FidelityScorer
com.paperlineage.chat        — ChatService, HybridQueryEngine
com.paperlineage.graph       — Neo4j repositories and domain nodes
com.paperlineage.vector      — pgvector client and EmbeddingClient
com.paperlineage.api         — REST controllers and WebSocket handler
com.paperlineage.config      — Spring config, beans, properties

## Data flows

### Ingestion (zero LLM)
POST /api/ingest?arxivId=... →
  1. arXiv API → fetch metadata
  2. PDFBox → parse abstract + methods section text
  3. Semantic Scholar → fetch forward + backward citations
  4. GitHub Search API → find repos implementing the paper
  5. HuggingFace API → embed paper sections + repo READMEs
  6. Cosine similarity → compute fidelity score per repo (no LLM)
  7. Neo4j → write (:Paper), (:Repo), (:Author) nodes with [:CITES], [:IMPLEMENTED_BY] edges
  8. pgvector → store embeddings
  9. Cache result by arXiv ID — never re-ingest same paper

### Chat (one LLM call)
WebSocket message →
  1. pgvector similarity search → top-k relevant chunks
  2. Neo4j graph traversal → structurally related entities
  3. Merge + rank results → 800-word context window
  4. Single Claude Haiku API call → stream answer back via WebSocket

## Conventions
- Java 21 records for all DTOs. No Lombok. No setters. Immutable only.
- Wrap all API responses in: record ApiResponse<T>(T data, String error, Map<String,Object> meta)
- @Cacheable on IngestionService.ingest() keyed by arXiv ID
- All secrets via env vars only. application.yml uses ${VAR_NAME:} placeholders.
- Tests: JUnit 5 + AssertJ. External APIs mocked with WireMock.
- Logging: SLF4J structured only. Zero System.out.

## Env vars required
ANTHROPIC_API_KEY
GITHUB_TOKEN
HUGGINGFACE_API_KEY
SEMANTIC_SCHOLAR_API_KEY
NEO4J_URI
NEO4J_USERNAME
NEO4J_PASSWORD
SUPABASE_URL
SUPABASE_SERVICE_KEY

## Build phases — do ONE per Claude Code session, stop after each
Phase 1:  Spring Boot skeleton + /health endpoint + Railway deploy config
Phase 2:  arXiv API client + PDFBox parser — CLI test prints title + abstract
Phase 3:  Semantic Scholar citation fetcher — CLI test prints forward + backward citations
Phase 4:  GitHub repo searcher — CLI test prints top 5 repos for a paper
Phase 5:  HuggingFace embedding client + pgvector storage — integration test embeds + retrieves
Phase 6:  Neo4j writer — integration test creates Paper + Repo + Citation nodes
Phase 7:  Full ingestion pipeline wired end-to-end — POST /api/ingest works
Phase 8:  HybridQueryEngine — GET /api/query works
Phase 9:  Claude chat service + WebSocket streaming
Phase 10: React frontend — D3 force graph + sidebar + chat panel + Vercel deploy

Commit and deploy after each phase before starting the next.

## Session rules
- Start every session with /caveman lite
- Reference this file with @CLAUDE.md instead of re-explaining architecture
- Use context7 for any Spring Boot, Neo4j, pgvector, or HuggingFace API questions
- Run /compact before ending each session
- One phase per session. Do not start phase N+1 until phase N is committed + deployed.

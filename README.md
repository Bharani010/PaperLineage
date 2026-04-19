# PaperLineage

> Trace a research paper's entire citation lineage, discover every GitHub implementation, and score each repo's runnability — all in one interactive graph. Ask questions about the literature via a streaming RAG-powered chat.

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-brightgreen?logo=spring)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-61dafb?logo=react)](https://react.dev)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-blue?logo=typescript)](https://www.typescriptlang.org)
[![Neo4j](https://img.shields.io/badge/Neo4j-AuraDB-00b3a0?logo=neo4j)](https://neo4j.com/cloud/platform/aura-graph-database/)
[![pgvector](https://img.shields.io/badge/pgvector-Supabase-3ecf8e?logo=supabase)](https://supabase.com)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

---

## What It Does

Enter any arXiv ID or paper URL. PaperLineage:

1. **Fetches** metadata from the arXiv API and parses the PDF for cited GitHub repos
2. **Traces** forward and backward citations via Semantic Scholar
3. **Discovers** GitHub implementations with a 3-strategy search (PDF link extraction → arXiv ID scan → title keywords)
4. **Scores** each repo across 6 dimensions (CI, Docker, deps, recency, issue ratio, stars) → 0–100 runnability score
5. **Embeds** paper sections + repo READMEs with Cohere and stores them in pgvector
6. **Writes** a fully connected citation and implementation graph to Neo4j
7. **Renders** it all as an interactive D3.js force graph — click any node for details
8. **Answers** questions about the literature via WebSocket streaming with a Groq LLM and hybrid RAG retrieval

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│  Frontend  (Vercel)                                                 │
│  React 18 · TypeScript · Vite · D3.js force graph · Ubuntu font    │
│  ┌──────────────┐  ┌────────────────────┐  ┌─────────────────────┐ │
│  │  Left Panel  │  │   Center: D3 Graph │  │  Right: RAG Chat    │ │
│  │  Paper/Repo  │  │   Force simulation │  │  WebSocket stream   │ │
│  │  detail +    │  │   Glass nodes      │  │  Groq LLaMA 3.3 70B │ │
│  │  run guide   │  │   Hover glow FX    │  │                     │ │
│  └──────────────┘  └────────────────────┘  └─────────────────────┘ │
└──────────────────────────┬──────────────────────────────────────────┘
                           │  REST + WebSocket
┌──────────────────────────▼──────────────────────────────────────────┐
│  Backend  (Render)                                                  │
│  Spring Boot 3.3 · Java 21 Virtual Threads · Maven                 │
│                                                                     │
│  POST /api/ingest                   WS /ws/chat                     │
│  ┌──────────────────────────────┐   ┌───────────────────────────┐  │
│  │  IngestionService            │   │  ChatWebSocketHandler     │  │
│  │  @Cacheable by arXiv ID      │   │  HybridQueryEngine        │  │
│  │                              │   │  ├─ pgvector top-k chunks │  │
│  │  ArxivClient (arXiv API)     │   │  ├─ Neo4j 1-hop traversal │  │
│  │  PdfParser (PDFBox)          │   │  └─ 800-word context      │  │
│  │  CitationFetcher (Sem.Schol.)│   │  ChatService → Groq SSE   │  │
│  │  RepoSearcher (GitHub API)   │   └───────────────────────────┘  │
│  │  FidelityScorer (cosine)     │                                   │
│  │  RunnabilityScorer (6-dim)   │                                   │
│  │  EmbeddingClient (Cohere)    │                                   │
│  └──────────────────────────────┘                                   │
└────────────────────┬───────────────────────────────────────────────┘
                     │
        ┌────────────┴────────────┐
        ▼                         ▼
┌───────────────┐        ┌──────────────────┐
│  Neo4j AuraDB │        │  Supabase        │
│  (:Paper)     │        │  pgvector table  │
│  (:Repo)      │        │  384-dim Cohere  │
│  (:Author)    │        │  embeddings      │
│  [:CITES]     │        │  match_embeddings│
│  [:IMPL_BY]   │        │  RPC function    │
└───────────────┘        └──────────────────┘
```

---

## Tech Stack

### Backend

| Layer | Technology | Why |
|---|---|---|
| Runtime | **Java 21** | Virtual threads (Project Loom) for non-blocking I/O without reactive boilerplate |
| Framework | **Spring Boot 3.3** | Dependency injection, WebSocket support, WebClient, structured config |
| HTTP client | **Spring WebClient** | Non-blocking reactive HTTP for all external API calls |
| Graph DB | **Neo4j AuraDB** | Native graph model for citation networks — CITES and IMPLEMENTED_BY relationships |
| Vector DB | **pgvector on Supabase** | Cosine similarity search over 384-dim Cohere embeddings via SQL RPC |
| Embeddings | **Cohere embed-english-light-v3.0** | Free-tier, 384 dimensions, high-quality semantic representations |
| LLM | **Groq — LLaMA 3.3 70B Versatile** | OpenAI-compatible API, free tier, streams SSE tokens at ~300 tok/s |
| PDF parsing | **Apache PDFBox** | Extracts abstract, methods section, and inline GitHub URLs from paper PDFs |
| Caching | **Spring @Cacheable** | In-memory cache keyed by arXiv ID — papers never re-ingested in the same session |
| Deploy | **Render** (Docker) | Free-tier web service with health-check wake-up |

### Frontend

| Layer | Technology | Why |
|---|---|---|
| Framework | **React 18** | Component model, concurrent features |
| Language | **TypeScript** | Type-safe API contracts, component props |
| Bundler | **Vite** | Sub-second HMR, optimised production builds |
| Graph viz | **D3.js** (force simulation) | Full control over node/link physics, custom SVG rendering |
| Styling | **Plain CSS** (custom design system) | Zero runtime cost, CSS variables, glass-morphism effects |
| Font | **Ubuntu / Ubuntu Mono** | Humanist sans-serif; optimised for screen clarity |
| Streaming | **WebSocket** | Token-by-token streaming from backend to chat panel |
| Deploy | **Vercel** | Edge CDN, instant deploys from `main` |

### External APIs (all free tier)

| API | Usage |
|---|---|
| arXiv API | Paper metadata: title, authors, abstract, year, PDF URL |
| Semantic Scholar | Forward + backward citation counts and IDs |
| GitHub Search API | Repository discovery by arXiv ID, title keywords, and PDF-extracted URLs |
| Papers With Code | Task labels, method names, SOTA benchmark results |
| HuggingFace Hub | Models citing the paper, pipeline tags, download counts |
| Cohere Embed | Semantic embeddings for paper sections and repo READMEs |
| Groq Chat | LLaMA 3.3 70B streaming completions for RAG chat |

---

## Core Pipelines

### Ingestion (Zero LLM)

Every piece of data is deterministic — no LLM is used during ingestion. The pipeline runs I/O steps in parallel using **Java 21 Virtual Threads** via `CompletableFuture.supplyAsync()` on a `VirtualThreadPerTaskExecutor`:

```
POST /api/ingest?arxivId=1706.03762
          │
          ▼
1. arXiv API          → title, authors, year, abstract, PDF URL
2. PDFBox             → parse abstract + methods text + extract GitHub URLs from first 2 pages
3. Semantic Scholar   → forward citations (cited by N) + backward citations (references M)
          │
          ├── [parallel via Virtual Threads] ──────────────────────────────────────┐
          │                                                                         │
4. GitHub Search      → 3 strategies:                                              │
   Strategy 1: Direct API call for each URL extracted from PDF                     │
   Strategy 2: Search "arxivId" in:readme,description,name                         │
   Strategy 3: Title keyword fallback                                               │
   → deduplicated via LinkedHashSet                                                 │
                                                                                    │
5. Papers With Code   → tasks, methods, SOTA benchmark results                     │
6. HuggingFace Hub    → models citing the paper                                    │
          │                                                                         │
          └────────────────────────────────────────────────────────────────────────┘
          │
7. Cohere Embed       → abstract vector + methods vector → stored in pgvector
   + repo READMEs embedded → cosine similarity with paper → fidelity score
          │
8. RunnabilityScorer  → 6-signal composite score (0–100) per repo:
   ├─ CI badge present       (0–25 pts)
   ├─ Dockerfile / conda     (0–20 pts)
   ├─ requirements.txt etc.  (0–15 pts)
   ├─ Days since last commit  (0–20 pts)
   ├─ Open issue ratio        (0–10 pts)
   └─ Star count              (0–10 pts)
   Labels: "Run it" ≥71 · "Risky" 41–70 · "Don't bother" <41
          │
9. Neo4j Writer       → (:Paper)-[:CITES]->(:Paper)
                        (:Paper)-[:IMPLEMENTED_BY]->(:Repo)
                        (:Paper)-[:AUTHORED_BY]->(:Author)
          │
10. @Cacheable        → result cached in-memory keyed by arXiv ID
```

### Chat (One LLM Call Per Message)

```
WebSocket message: { "message": "How does attention compare to RNNs?" }
          │
          ▼
1. HybridQueryEngine
   ├─ Cohere embed query → 384-dim vector
   ├─ pgvector match_embeddings RPC → top-5 semantically similar chunks
   └─ Neo4j 1-hop traversal → papers cited by / citing matched papers
          │
2. Context builder → merge chunks + graph nodes → truncate at ~800 words
          │
3. ChatService → Groq API (OpenAI-compatible SSE stream)
   System prompt: research assistant persona
   User message:  context + question
          │
4. WebSocket stream → token-by-token to frontend
   Frontend renders each chunk in real time with blinking cursor
```

---

## Features

### Interactive Citation Graph
- D3.js force simulation with configurable charge, collision, and link distance
- **Glass-morphism nodes** — radial SVG gradients with top-left shine ellipses
- Node size scales with paper citation count / repo star count
- Colour-coded: blue (paper) · green (Run it) · amber (Risky) · red (Don't bother)
- Pulsing ring on root paper; glow intensifies on hover
- Labels grow and brighten on cursor approach with SVG blur filter glow
- Arrows correctly trim to node surface — computed per-tick from actual radii

### Paper Detail Panel
- Fraunces-styled title, author chips, arXiv link
- 3-stat grid: cited by / references / repos found
- Papers With Code: tasks, methods, SOTA benchmark table
- HuggingFace: linked model cards with pipeline tags + download counts
- Ranked implementation list with runnability badge per repo

### Repo Detail + AI Run Guide
- 0–100 score with animated fill bar
- CI / Docker / Deps / recency signal badges
- **"How to Run" AI guide** — single Groq call (JSON mode) generates difficulty, prerequisites, step-by-step commands, and common issues. Cached by repo name.

### Streaming Research Chat
- WebSocket connection with automatic Render cold-start wake-up
- Exponential backoff reconnect (up to 10 retries, 70s deadline)
- Thinking dots animation while awaiting first token
- Source tags show which chunks grounded the answer

### Rankings Leaderboard
- All ingested papers ranked by best runnability score
- Score bar, CI/Docker/Deps dots, star count, commit age
- Click to jump to graph view, share button copies `?p=arxivId` URL

### Shareable URLs
- `?p=1706.03762` auto-ingests on page load
- Supports full URLs: `arxiv.org/abs/...`, `alphaxiv.org/abs/...`, bare IDs

---

## Project Structure

```
paperlineage/
├── src/main/java/com/paperlineage/
│   ├── api/                  # REST controllers + WebSocket handler
│   │   ├── IngestionController.java
│   │   ├── ChatWebSocketHandler.java
│   │   ├── RunGuideController.java
│   │   └── ApiResponse.java  # Generic wrapper record
│   ├── ingestion/            # Zero-LLM ingestion pipeline
│   │   ├── IngestionService.java     # Orchestrator + @Cacheable
│   │   ├── ArxivClient.java          # arXiv Atom API
│   │   ├── PdfParser.java            # PDFBox + GitHub URL regex
│   │   ├── CitationFetcher.java      # Semantic Scholar
│   │   ├── RepoSearcher.java         # 3-strategy GitHub search
│   │   ├── RunnabilityScore.java     # 6-signal composite record
│   │   ├── ReproducibilityScorer.java
│   │   ├── FidelityScorer.java       # Cosine similarity (hand-rolled)
│   │   ├── PwcClient.java            # Papers With Code
│   │   ├── HuggingFaceClient.java
│   │   └── RunGuideService.java      # AI guide, @Cacheable
│   ├── chat/                 # RAG chat pipeline
│   │   ├── HybridQueryEngine.java    # Vector + graph context builder
│   │   └── ChatService.java          # Groq SSE streaming
│   ├── graph/                # Neo4j domain + repositories
│   │   ├── Neo4jWriter.java
│   │   ├── PaperNode.java / RepoNode.java / AuthorNode.java
│   │   └── PaperRepository.java / RepoRepository.java
│   ├── vector/               # pgvector / Cohere
│   │   ├── EmbeddingClient.java      # Cohere embed API
│   │   └── PgVectorClient.java       # Supabase REST RPC
│   └── config/               # Spring beans and settings
│
├── frontend/src/
│   ├── components/
│   │   ├── ForceGraph.tsx     # D3 simulation, glass nodes, hover FX
│   │   ├── PaperSidebar.tsx   # Paper/repo detail panel
│   │   ├── ChatPanel.tsx      # WebSocket streaming chat
│   │   ├── TopBar.tsx         # Nav + URL input + view toggle
│   │   └── LeaderboardView.tsx
│   ├── hooks/
│   │   └── useChat.ts         # WebSocket lifecycle, reconnect, streaming
│   ├── styles/globals.css     # Design system, glass buttons, animations
│   └── api.ts                 # Typed fetch helpers
│
├── render.yaml                # Render deploy config (Docker)
└── Dockerfile                 # Multi-stage: Maven build → JRE 21 slim
```

---

## API Reference

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/health` | Health check — used by frontend to wake Render from sleep |
| `POST` | `/api/ingest?arxivId={id}` | Run full ingestion pipeline; returns `ApiResponse<IngestionResult>` |
| `GET` | `/api/query?q={text}` | Hybrid vector+graph search; returns ranked context chunks |
| `GET` | `/api/run-guide` | Generate AI how-to-run guide for a repo (cached by repo name) |
| `WS` | `/ws/chat` | WebSocket endpoint; send `{"message":"..."}`, receive `{"chunk":"..."}` / `{"done":true,"sources":[...]}` |

All REST responses follow the generic wrapper:
```json
{ "data": { ... }, "error": null, "meta": { "durationMs": 1240 } }
```

---

## Running Locally

### Prerequisites

- Java 21+
- Node 18+
- A free account on: Neo4j AuraDB · Supabase · Cohere · Groq · GitHub

### Backend

```bash
# Copy and fill in your credentials
cp src/main/resources/application.yml.example src/main/resources/application.yml

export GROQ_API_KEY=...
export COHERE_API_KEY=...
export GITHUB_TOKEN=...
export NEO4J_URI=neo4j+s://xxxx.databases.neo4j.io
export NEO4J_USERNAME=neo4j
export NEO4J_PASSWORD=...
export SUPABASE_URL=https://xxxx.supabase.co
export SUPABASE_SERVICE_KEY=...
export SEMANTIC_SCHOLAR_API_KEY=...   # optional, higher rate limit

mvn spring-boot:run
# → http://localhost:8080
```

### Supabase Setup

Run this once in the Supabase SQL editor to enable pgvector and create the table + RPC:

```sql
create extension if not exists vector;

create table embeddings (
  id bigserial primary key,
  source text not null,
  chunk_text text,
  embedding vector(384)
);

create or replace function match_embeddings(
  query_embedding vector(384),
  match_count int
)
returns table (id bigint, source text, chunk_text text, similarity float)
language sql stable as $$
  select id, source, chunk_text,
         1 - (embedding <=> query_embedding) as similarity
  from embeddings
  order by embedding <=> query_embedding
  limit match_count;
$$;
```

### Frontend

```bash
cd frontend
cp .env.example .env.local
# Set VITE_API_BASE_URL=http://localhost:8080

npm install
npm run dev
# → http://localhost:5173
```

---

## Design Decisions

**Why Neo4j for citations?**
Citation networks are inherently graph-shaped. A 1-hop traversal (`MATCH (p)-[:CITES]->(q)`) returns structural context that flat tables cannot express without expensive joins. Neo4j AuraDB gives us this for free.

**Why pgvector for embeddings?**
Supabase's Postgres + pgvector extension lets us store and query embeddings using a single SQL function call (`<=>` cosine operator). No separate vector DB service to manage.

**Why Groq instead of OpenAI?**
Groq's free tier provides LLaMA 3.3 70B at ~300 tokens/second — faster than GPT-4o on paid tiers. It's OpenAI-compatible, so switching models is one constant change.

**Why Java 21 Virtual Threads?**
The ingestion pipeline fans out 4+ independent HTTP calls (citations, PWC, HuggingFace, GitHub). Virtual threads let us write plain blocking code (`future.join()`) without callbacks or reactive pipelines, while the JVM schedules them on a small carrier thread pool. No thread pool tuning needed.

**Why zero LLM during ingestion?**
Deterministic pipelines are reproducible, debuggable, and free. The runnability score uses explicit heuristics (not LLM judgment), so scores are consistent and explainable.

**Why a 3-strategy GitHub search?**
Papers rarely name their repo in a consistent way. Strategy 1 (PDF URL extraction) is highest signal; Strategy 2 (arXiv ID in README) catches well-documented repos; Strategy 3 (title keywords) is the fallback. Each strategy is deduped via `LinkedHashSet`.

---

## Deployment

| Service | Platform | Config |
|---|---|---|
| Backend | Render (Docker, free tier) | `render.yaml` — health check at `/api/health` |
| Frontend | Vercel | Auto-deploy on `git push main`; `VITE_API_BASE_URL` env var |
| Graph DB | Neo4j AuraDB Free | 200MB limit, no expiry |
| Vector DB | Supabase Free | 500MB Postgres, pgvector extension |

The frontend pings `/api/health` before opening the WebSocket to wake Render's free-tier instance from sleep (up to 60s cold start). If the WebSocket drops, exponential backoff reconnects automatically.

---

## License

MIT — see [LICENSE](LICENSE).

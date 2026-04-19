package com.paperlineage.ingestion;

import com.paperlineage.graph.Neo4jWriter;
import com.paperlineage.vector.EmbeddingClient;
import com.paperlineage.vector.PgVectorClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final ArxivClient arxivClient;
    private final PdfParser pdfParser;
    private final CitationFetcher citationFetcher;
    private final RepoSearcher repoSearcher;
    private final ReproducibilityScorer reproducibilityScorer;
    private final PwcClient pwcClient;
    private final HuggingFaceClient hfClient;
    private final EmbeddingClient embeddingClient;
    private final PgVectorClient pgVectorClient;
    private final Neo4jWriter neo4jWriter;

    // Virtual threads (Java 21) for I/O-heavy parallel work
    private final ExecutorService ioPool = Executors.newVirtualThreadPerTaskExecutor();

    public IngestionService(ArxivClient arxivClient, PdfParser pdfParser,
                            CitationFetcher citationFetcher, RepoSearcher repoSearcher,
                            ReproducibilityScorer reproducibilityScorer,
                            PwcClient pwcClient, HuggingFaceClient hfClient,
                            EmbeddingClient embeddingClient, PgVectorClient pgVectorClient,
                            Neo4jWriter neo4jWriter) {
        this.arxivClient           = arxivClient;
        this.pdfParser             = pdfParser;
        this.citationFetcher       = citationFetcher;
        this.repoSearcher          = repoSearcher;
        this.reproducibilityScorer = reproducibilityScorer;
        this.pwcClient             = pwcClient;
        this.hfClient              = hfClient;
        this.embeddingClient       = embeddingClient;
        this.pgVectorClient        = pgVectorClient;
        this.neo4jWriter           = neo4jWriter;
    }

    @Cacheable(value = "ingestion", key = "#arxivId")
    public IngestionResult ingest(String arxivId) {
        log.info("Starting ingestion for arxivId={}", arxivId);

        // 1-2. Fetch metadata + parse PDF text
        PaperMetadata meta = pdfParser.enrich(arxivClient.fetch(arxivId));

        // 3-4. Fan out: citations, PWC, HF, GitHub repos — all independent
        CompletableFuture<CitationGraph>    citationsFuture = CompletableFuture
                .supplyAsync(() -> citationFetcher.fetch(arxivId), ioPool);

        CompletableFuture<PwcData>          pwcFuture = CompletableFuture
                .supplyAsync(() -> pwcClient.fetch(arxivId), ioPool);

        CompletableFuture<List<HfModelInfo>> hfFuture = CompletableFuture
                .supplyAsync(() -> hfClient.search(arxivId, meta.title()), ioPool);

        CompletableFuture<List<RepoResult>> reposFuture = CompletableFuture
                .supplyAsync(() -> repoSearcher.search(meta), ioPool);

        // 5. Embed paper sections → pgvector (while others run)
        float[] abstractEmbedding = embedAndStore("arxiv:" + arxivId + ":abstract", meta.abstractText());
        if (meta.methodsText() != null && !meta.methodsText().isBlank()) {
            embedAndStore("arxiv:" + arxivId + ":methods", meta.methodsText());
        }

        // 6. Wait for all async work
        CitationGraph       citations = citationsFuture.join();
        PwcData             pwcData   = pwcFuture.join();
        List<HfModelInfo>   hfModels  = hfFuture.join();
        List<RepoResult>    repos     = reposFuture.join();

        // 7. Score repos + embed descriptions
        List<Neo4jWriter.ScoredRepo> scoredRepos = repos.stream()
                .map(repo -> {
                    RunnabilityScore score = reproducibilityScorer.score(repo);
                    embedAndStore("github:" + repo.fullName(), repoText(repo));
                    return new Neo4jWriter.ScoredRepo(repo, score);
                })
                .toList();

        // 8. Write to Neo4j
        neo4jWriter.write(meta, citations, scoredRepos, pwcData, hfModels);

        log.info("Ingestion complete arxivId={} repos={} tasks={} hfModels={}",
                arxivId, scoredRepos.size(), pwcData.tasks().size(), hfModels.size());

        return new IngestionResult(
                arxivId, meta.title(), meta.year(), meta.authors(),
                citations.totalForward(), citations.totalBackward(),
                scoredRepos.stream()
                        .map(sr -> new IngestionResult.ScoredRepo(
                                sr.repo().fullName(), sr.repo().url(), sr.repo().stars(),
                                sr.score().total(), sr.score().label(),
                                sr.score().hasCi(), sr.score().hasDocker(),
                                sr.score().hasDeps(), sr.score().daysSinceCommit()))
                        .sorted((a, b) -> Integer.compare(b.runnabilityScore(), a.runnabilityScore()))
                        .toList(),
                pwcData,
                hfModels
        );
    }

    private float[] embedAndStore(String source, String text) {
        try {
            float[] embedding = embeddingClient.embed(text);
            if (embedding.length > 0) pgVectorClient.store(source, text, embedding);
            return embedding;
        } catch (Exception e) {
            log.warn("Embed/store failed source={}: {}", source, e.getMessage());
            return new float[0];
        }
    }

    private String repoText(RepoResult repo) {
        String desc = repo.description();
        return (desc != null && !desc.isBlank()) ? desc : repo.fullName();
    }
}

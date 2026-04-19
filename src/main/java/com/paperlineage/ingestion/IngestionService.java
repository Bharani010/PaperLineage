package com.paperlineage.ingestion;

import com.paperlineage.graph.Neo4jWriter;
import com.paperlineage.vector.EmbeddingClient;
import com.paperlineage.vector.PgVectorClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final ArxivClient arxivClient;
    private final PdfParser pdfParser;
    private final CitationFetcher citationFetcher;
    private final RepoSearcher repoSearcher;
    private final ReproducibilityScorer reproducibilityScorer;
    private final EmbeddingClient embeddingClient;
    private final PgVectorClient pgVectorClient;
    private final Neo4jWriter neo4jWriter;

    public IngestionService(ArxivClient arxivClient, PdfParser pdfParser,
                            CitationFetcher citationFetcher, RepoSearcher repoSearcher,
                            ReproducibilityScorer reproducibilityScorer,
                            EmbeddingClient embeddingClient, PgVectorClient pgVectorClient,
                            Neo4jWriter neo4jWriter) {
        this.arxivClient           = arxivClient;
        this.pdfParser             = pdfParser;
        this.citationFetcher       = citationFetcher;
        this.repoSearcher          = repoSearcher;
        this.reproducibilityScorer = reproducibilityScorer;
        this.embeddingClient       = embeddingClient;
        this.pgVectorClient        = pgVectorClient;
        this.neo4jWriter           = neo4jWriter;
    }

    @Cacheable(value = "ingestion", key = "#arxivId")
    public IngestionResult ingest(String arxivId) {
        log.info("Starting ingestion for arxivId={}", arxivId);

        // 1-2. Fetch metadata + parse PDF
        PaperMetadata meta = pdfParser.enrich(arxivClient.fetch(arxivId));

        // 3. Citations
        CitationGraph citations = citationFetcher.fetch(arxivId);

        // 4. Find GitHub repos
        List<RepoResult> repos = repoSearcher.search(meta);

        // 5. Embed paper sections → pgvector
        float[] abstractEmbedding = embedAndStore("arxiv:" + arxivId + ":abstract", meta.abstractText());
        if (meta.methodsText() != null && !meta.methodsText().isBlank()) {
            embedAndStore("arxiv:" + arxivId + ":methods", meta.methodsText());
        }

        // 6. Score each repo on runnability + embed description → pgvector
        List<Neo4jWriter.ScoredRepo> scoredRepos = repos.stream()
                .map(repo -> {
                    RunnabilityScore score = reproducibilityScorer.score(repo);
                    embedAndStore("github:" + repo.fullName(), repoText(repo));
                    return new Neo4jWriter.ScoredRepo(repo, score);
                })
                .toList();

        // 7. Write to Neo4j
        neo4jWriter.write(meta, citations, scoredRepos);

        log.info("Ingestion complete for arxivId={} repos={}", arxivId, scoredRepos.size());

        return new IngestionResult(
                arxivId,
                meta.title(),
                meta.year(),
                meta.authors(),
                citations.totalForward(),
                citations.totalBackward(),
                scoredRepos.stream()
                        .map(sr -> new IngestionResult.ScoredRepo(
                                sr.repo().fullName(),
                                sr.repo().url(),
                                sr.repo().stars(),
                                sr.score().total(),
                                sr.score().label(),
                                sr.score().hasCi(),
                                sr.score().hasDocker(),
                                sr.score().hasDeps(),
                                sr.score().daysSinceCommit()))
                        .sorted((a, b) -> Integer.compare(b.runnabilityScore(), a.runnabilityScore()))
                        .toList()
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

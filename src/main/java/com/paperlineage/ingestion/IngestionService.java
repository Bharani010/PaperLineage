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
    private final EmbeddingClient embeddingClient;
    private final PgVectorClient pgVectorClient;
    private final FidelityScorer fidelityScorer;
    private final Neo4jWriter neo4jWriter;

    public IngestionService(ArxivClient arxivClient, PdfParser pdfParser,
                            CitationFetcher citationFetcher, RepoSearcher repoSearcher,
                            EmbeddingClient embeddingClient, PgVectorClient pgVectorClient,
                            FidelityScorer fidelityScorer, Neo4jWriter neo4jWriter) {
        this.arxivClient = arxivClient;
        this.pdfParser = pdfParser;
        this.citationFetcher = citationFetcher;
        this.repoSearcher = repoSearcher;
        this.embeddingClient = embeddingClient;
        this.pgVectorClient = pgVectorClient;
        this.fidelityScorer = fidelityScorer;
        this.neo4jWriter = neo4jWriter;
    }

    @Cacheable(value = "ingestion", key = "#arxivId")
    public IngestionResult ingest(String arxivId) {
        log.info("Starting ingestion for arxivId={}", arxivId);

        // 1-2. Fetch metadata + parse PDF text
        PaperMetadata meta = pdfParser.enrich(arxivClient.fetch(arxivId));

        // 3. Citations
        CitationGraph citations = citationFetcher.fetch(arxivId);

        // 4. GitHub repos
        List<RepoResult> repos = repoSearcher.search(meta);

        // 5. Embed paper sections and store to pgvector
        float[] abstractEmbedding = embedAndStore(
                "arxiv:" + arxivId + ":abstract", meta.abstractText());
        if (meta.methodsText() != null && !meta.methodsText().isBlank()) {
            embedAndStore("arxiv:" + arxivId + ":methods", meta.methodsText());
        }

        // 6. Embed repo descriptions, compute fidelity, store to pgvector
        List<Neo4jWriter.ScoredRepo> scoredRepos = repos.stream()
                .map(repo -> {
                    String repoText = repoDescription(repo);
                    float[] repoEmbedding = embedAndStore("github:" + repo.fullName(), repoText);
                    double score = fidelityScorer.score(abstractEmbedding, repoEmbedding);
                    return new Neo4jWriter.ScoredRepo(repo, score);
                })
                .toList();

        // 7. Write to Neo4j
        neo4jWriter.write(meta, citations, scoredRepos);

        log.info("Ingestion complete for arxivId={}", arxivId);

        return new IngestionResult(
                arxivId,
                meta.title(),
                meta.year(),
                meta.authors(),
                citations.totalForward(),
                citations.totalBackward(),
                scoredRepos.stream()
                        .map(sr -> new IngestionResult.ScoredRepo(
                                sr.repo().fullName(), sr.repo().url(),
                                sr.repo().stars(), sr.fidelityScore()))
                        .toList()
        );
    }

    private float[] embedAndStore(String source, String text) {
        float[] embedding = embeddingClient.embed(text);
        pgVectorClient.store(source, text, embedding);
        return embedding;
    }

    private String repoDescription(RepoResult repo) {
        String desc = repo.description();
        return (desc != null && !desc.isBlank()) ? desc : repo.fullName();
    }
}

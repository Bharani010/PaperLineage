package com.paperlineage.chat;

import com.paperlineage.graph.PaperNode;
import com.paperlineage.graph.PaperRepository;
import com.paperlineage.vector.EmbeddingClient;
import com.paperlineage.vector.EmbeddingChunk;
import com.paperlineage.vector.PgVectorClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class HybridQueryEngine {

    private static final Logger log = LoggerFactory.getLogger(HybridQueryEngine.class);
    private static final int TOP_K_VECTOR = 5;
    private static final int MAX_CONTEXT_CHARS = 4800; // ~800 words

    private final EmbeddingClient embeddingClient;
    private final PgVectorClient pgVectorClient;
    private final PaperRepository paperRepository;

    public HybridQueryEngine(EmbeddingClient embeddingClient,
                              PgVectorClient pgVectorClient,
                              PaperRepository paperRepository) {
        this.embeddingClient = embeddingClient;
        this.pgVectorClient = pgVectorClient;
        this.paperRepository = paperRepository;
    }

    public QueryResult query(String question) {
        log.info("HybridQueryEngine: question='{}'", question);

        float[] queryVec = embeddingClient.embed(question);

        List<EmbeddingChunk> vectorChunks = pgVectorClient.findSimilar(queryVec, TOP_K_VECTOR);
        log.info("Vector search returned {} chunks", vectorChunks.size());

        Set<String> arxivIds = extractArxivIds(vectorChunks);
        List<PaperNode> graphNodes = fetchGraphContext(arxivIds);
        log.info("Graph traversal returned {} paper nodes", graphNodes.size());

        List<String> sources = new ArrayList<>();
        StringBuilder context = buildContext(vectorChunks, graphNodes, sources);

        return new QueryResult(context.toString(), sources, vectorChunks.size(), graphNodes.size());
    }

    private Set<String> extractArxivIds(List<EmbeddingChunk> chunks) {
        Set<String> ids = new LinkedHashSet<>();
        for (EmbeddingChunk chunk : chunks) {
            if (chunk.source() != null && chunk.source().startsWith("arxiv:")) {
                // source format: "arxiv:{id}:abstract" or "arxiv:{id}:methods"
                String[] parts = chunk.source().split(":", 3);
                if (parts.length >= 2) {
                    ids.add(parts[1]);
                }
            }
        }
        return ids;
    }

    private List<PaperNode> fetchGraphContext(Set<String> arxivIds) {
        List<PaperNode> nodes = new ArrayList<>();
        for (String id : arxivIds) {
            paperRepository.findById(id).ifPresent(paper -> {
                nodes.add(paper);
                // include direct citations (one hop)
                if (paper.citations() != null) {
                    nodes.addAll(paper.citations());
                }
            });
        }
        return nodes;
    }

    private StringBuilder buildContext(List<EmbeddingChunk> chunks, List<PaperNode> graphNodes, List<String> sources) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== Relevant Paper Sections ===\n");
        for (EmbeddingChunk chunk : chunks) {
            if (chunk.chunkText() != null && !chunk.chunkText().isBlank()) {
                String snippet = chunk.chunkText().length() > 600
                        ? chunk.chunkText().substring(0, 600) + "..."
                        : chunk.chunkText();
                sb.append(snippet).append("\n\n");
                if (chunk.source() != null) sources.add(chunk.source());
            }
            if (sb.length() >= MAX_CONTEXT_CHARS) break;
        }

        if (!graphNodes.isEmpty() && sb.length() < MAX_CONTEXT_CHARS) {
            sb.append("=== Related Papers (Graph) ===\n");
            for (PaperNode node : graphNodes) {
                if (node.title() != null) {
                    sb.append("- ").append(node.title());
                    if (node.year() > 0) sb.append(" (").append(node.year()).append(")");
                    sb.append("\n");
                    if (node.abstractText() != null && !node.abstractText().isBlank()) {
                        String abs = node.abstractText().length() > 200
                                ? node.abstractText().substring(0, 200) + "..."
                                : node.abstractText();
                        sb.append("  ").append(abs).append("\n");
                    }
                }
                if (sb.length() >= MAX_CONTEXT_CHARS) break;
            }
        }

        return sb;
    }
}

package com.paperlineage.graph;

import com.paperlineage.ingestion.CitationEntry;
import com.paperlineage.ingestion.CitationGraph;
import com.paperlineage.ingestion.PaperMetadata;
import com.paperlineage.ingestion.RepoResult;
import com.paperlineage.ingestion.RunnabilityScore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "NEO4J_URI", matches = ".+")
class Neo4jIntegrationTest {

    private static final String TEST_ARXIV_ID = "test:phase6:2301.00001";
    private static final String CITATION_ID   = "test:phase6:2301.00002";

    @Autowired
    private Neo4jWriter neo4jWriter;

    @Autowired
    private PaperRepository paperRepository;

    @AfterEach
    void cleanup() {
        paperRepository.deleteById(TEST_ARXIV_ID);
        paperRepository.deleteById(CITATION_ID);
    }

    @Test
    void writesPaperWithRepoAndCitationNodes() {
        PaperMetadata metadata = new PaperMetadata(
                TEST_ARXIV_ID,
                "Attention Is All You Need",
                List.of("Vaswani", "Shazeer"),
                2017,
                "We propose the Transformer...",
                "Multi-head self-attention...",
                "https://arxiv.org/pdf/1706.03762",
                List.of()
        );

        CitationEntry citation = new CitationEntry(CITATION_ID, "BERT", List.of("Devlin"), 2018, 50000);
        CitationGraph citationGraph = new CitationGraph(
                TEST_ARXIV_ID, metadata.title(),
                List.of(citation), List.of(),
                1, 0
        );

        RepoResult repo = new RepoResult(
                "huggingface/transformers",
                "https://github.com/huggingface/transformers",
                "Transformers library",
                "Python",
                100000,
                20000,
                50
        );

        PaperNode saved = neo4jWriter.write(metadata, citationGraph,
                List.of(new Neo4jWriter.ScoredRepo(repo, RunnabilityScore.zero())),
                com.paperlineage.ingestion.PwcData.notFound(),
                List.of());

        assertThat(saved.arxivId()).isEqualTo(TEST_ARXIV_ID);
        assertThat(saved.authors()).hasSize(2);
        assertThat(saved.repos()).hasSize(1);
        assertThat(saved.repos().get(0).fullName()).isEqualTo("huggingface/transformers");
        assertThat(saved.citations()).hasSize(1);
        assertThat(saved.citations().get(0).arxivId()).isEqualTo(CITATION_ID);

        Optional<PaperNode> loaded = paperRepository.findById(TEST_ARXIV_ID);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().title()).isEqualTo("Attention Is All You Need");
    }
}

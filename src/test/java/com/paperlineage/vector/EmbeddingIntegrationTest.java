package com.paperlineage.vector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "HUGGINGFACE_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SUPABASE_URL", matches = ".+")
class EmbeddingIntegrationTest {

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private PgVectorClient pgVectorClient;

    private String testSource;

    @AfterEach
    void cleanup() {
        if (testSource != null) {
            pgVectorClient.deleteBySource(testSource);
        }
    }

    @Test
    void embedsTextAndProduces384Dimensions() {
        float[] embedding = embeddingClient.embed("Attention is all you need");
        assertThat(embedding).hasSize(384);
        assertThat(embedding[0]).isNotZero();
    }

    @Test
    void storesAndRetrievesChunkBySimilarity() {
        testSource = "test:phase5:" + UUID.randomUUID();
        String chunkText = "Transformers use multi-head self-attention mechanisms";

        float[] embedding = embeddingClient.embed(chunkText);
        pgVectorClient.store(testSource, chunkText, embedding);

        List<EmbeddingChunk> results = pgVectorClient.findSimilar(embedding, 1);
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).source()).isEqualTo(testSource);
    }
}

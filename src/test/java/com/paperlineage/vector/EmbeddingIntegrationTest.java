package com.paperlineage.vector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "HUGGINGFACE_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SUPABASE_URL", matches = ".+")
class EmbeddingIntegrationTest {

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private PgVectorClient pgVectorClient;

    @Test
    void embedsTextAndProduces384Dimensions() {
        float[] embedding = embeddingClient.embed("Attention is all you need");
        assertThat(embedding).hasSize(384);
        assertThat(embedding[0]).isNotZero();
    }

    @Test
    void storesAndRetrievesChunkBySimilarity() {
        String source = "test:phase5";
        String chunkText = "Transformers use multi-head self-attention mechanisms";

        float[] embedding = embeddingClient.embed(chunkText);
        pgVectorClient.store(source, chunkText, embedding);

        List<EmbeddingChunk> results = pgVectorClient.findSimilar(embedding, 1);
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).chunkText()).isEqualTo(chunkText);
    }
}

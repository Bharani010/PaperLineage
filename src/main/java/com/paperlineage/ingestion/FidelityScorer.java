package com.paperlineage.ingestion;

import org.springframework.stereotype.Component;

@Component
public class FidelityScorer {

    public double score(float[] paperEmbedding, float[] repoEmbedding) {
        if (paperEmbedding == null || repoEmbedding == null
                || paperEmbedding.length == 0 || repoEmbedding.length == 0) {
            return 0.0;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < paperEmbedding.length; i++) {
            dot += (double) paperEmbedding[i] * repoEmbedding[i];
            normA += (double) paperEmbedding[i] * paperEmbedding[i];
            normB += (double) repoEmbedding[i] * repoEmbedding[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0.0 : dot / denom;
    }
}

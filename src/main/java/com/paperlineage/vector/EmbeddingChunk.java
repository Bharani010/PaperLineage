package com.paperlineage.vector;

public record EmbeddingChunk(
        Long id,
        String source,
        String chunkText,
        float[] embedding
) {}

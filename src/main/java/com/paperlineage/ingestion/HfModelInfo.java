package com.paperlineage.ingestion;

/**
 * A HuggingFace model card linked to a paper.
 */
public record HfModelInfo(
        String modelId,
        String pipelineTag,   // "text-generation", "image-classification", etc.
        int downloads,        // monthly downloads
        int likes,
        String lastModified   // ISO-8601 date string
) {
    public String url() {
        return "https://huggingface.co/" + modelId;
    }
}

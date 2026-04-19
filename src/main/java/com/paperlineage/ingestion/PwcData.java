package com.paperlineage.ingestion;

import java.util.List;

/**
 * Data fetched from Papers With Code for a single paper.
 * {@code found=false} means the paper isn't indexed on PWC — that itself is a signal.
 */
public record PwcData(
        boolean found,
        String pwcId,           // e.g. "attention-is-all-you-need"
        List<String> tasks,     // e.g. ["Machine Translation", "Language Modelling"]
        List<String> methods,   // e.g. ["Transformer", "Multi-Head Attention"]
        List<BenchmarkResult> topResults
) {
    public record BenchmarkResult(
            String task,
            String dataset,
            String metric,
            String value,
            boolean isSota
    ) {}

    public static PwcData notFound() {
        return new PwcData(false, null, List.of(), List.of(), List.of());
    }
}

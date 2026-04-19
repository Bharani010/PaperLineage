package com.paperlineage.ingestion;

import java.util.List;

public record IngestionResult(
        String arxivId,
        String title,
        int year,
        List<String> authors,
        int forwardCitations,
        int backwardCitations,
        List<ScoredRepo> repos,
        PwcData pwcData,
        List<HfModelInfo> hfModels
) {
    public record ScoredRepo(
            String fullName,
            String url,
            int stars,
            int runnabilityScore,
            String runnabilityLabel,
            boolean hasCi,
            boolean hasDocker,
            boolean hasDeps,
            int daysSinceCommit
    ) {}
}

package com.paperlineage.ingestion;

import java.util.List;

public record CitationGraph(
        String paperId,
        String title,
        List<CitationEntry> forwardCitations,
        List<CitationEntry> backwardCitations,
        int totalForward,
        int totalBackward
) {}

package com.paperlineage.ingestion;

import java.util.List;

public record CitationEntry(
        String paperId,
        String title,
        List<String> authors,
        Integer year,
        Integer citationCount
) {}

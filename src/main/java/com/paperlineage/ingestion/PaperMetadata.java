package com.paperlineage.ingestion;

import java.util.List;

public record PaperMetadata(
        String arxivId,
        String title,
        List<String> authors,
        int year,
        String abstractText,
        String methodsText,
        String pdfUrl
) {}

package com.paperlineage.ingestion;

public record RepoResult(
        String fullName,
        String url,
        String description,
        String language,
        int stars,
        int forks
) {}

package com.paperlineage.chat;

import java.util.List;

public record QueryResult(
        String context,
        List<String> sources,
        int chunkCount,
        int graphNodeCount
) {}

package com.paperlineage.graph;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Repo")
public record RepoNode(
        @Id String fullName,
        String url,
        String description,
        String language,
        int stars,
        int runnabilityScore,
        String runnabilityLabel,
        boolean hasCi,
        boolean hasDocker,
        boolean hasDeps,
        int daysSinceCommit
) {}

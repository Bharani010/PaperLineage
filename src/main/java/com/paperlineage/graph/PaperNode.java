package com.paperlineage.graph;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.List;

@Node("Paper")
public record PaperNode(
        @Id String arxivId,
        String title,
        int year,
        String abstractText,
        @Relationship(type = "AUTHORED_BY") List<AuthorNode> authors,
        @Relationship(type = "IMPLEMENTED_BY") List<RepoNode> repos,
        @Relationship(type = "CITES") List<PaperNode> citations
) {
    public PaperNode(String arxivId, String title, int year, String abstractText) {
        this(arxivId, title, year, abstractText, List.of(), List.of(), List.of());
    }
}

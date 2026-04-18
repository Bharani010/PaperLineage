package com.paperlineage.graph;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Author")
public record AuthorNode(@Id String name) {}

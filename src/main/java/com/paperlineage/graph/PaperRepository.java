package com.paperlineage.graph;

import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface PaperRepository extends Neo4jRepository<PaperNode, String> {}

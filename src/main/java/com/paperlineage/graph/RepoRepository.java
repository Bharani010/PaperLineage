package com.paperlineage.graph;

import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface RepoRepository extends Neo4jRepository<RepoNode, String> {}

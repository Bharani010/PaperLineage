package com.paperlineage.graph;

import com.paperlineage.ingestion.CitationEntry;
import com.paperlineage.ingestion.CitationGraph;
import com.paperlineage.ingestion.PaperMetadata;
import com.paperlineage.ingestion.RepoResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class Neo4jWriter {

    private static final Logger log = LoggerFactory.getLogger(Neo4jWriter.class);

    private final PaperRepository paperRepository;

    public Neo4jWriter(PaperRepository paperRepository) {
        this.paperRepository = paperRepository;
    }

    public record ScoredRepo(RepoResult repo, double fidelityScore) {}

    @Transactional
    public PaperNode write(PaperMetadata metadata, CitationGraph citations, List<ScoredRepo> repos) {
        log.info("Writing paper {} to Neo4j", metadata.arxivId());

        List<AuthorNode> authors = metadata.authors().stream()
                .map(AuthorNode::new)
                .toList();

        List<RepoNode> repoNodes = repos.stream()
                .map(sr -> new RepoNode(
                        sr.repo().fullName(), sr.repo().url(), sr.repo().description(),
                        sr.repo().language(), sr.repo().stars(), sr.fidelityScore()))
                .toList();

        List<PaperNode> citationNodes = citations.backwardCitations().stream()
                .map(this::toCitationStub)
                .toList();

        PaperNode paper = new PaperNode(
                metadata.arxivId(),
                metadata.title(),
                metadata.year(),
                metadata.abstractText(),
                authors,
                repoNodes,
                citationNodes
        );

        PaperNode saved = paperRepository.save(paper);
        log.info("Saved paper {} with {} authors, {} repos, {} citations",
                metadata.arxivId(), authors.size(), repoNodes.size(), citationNodes.size());
        return saved;
    }

    private PaperNode toCitationStub(CitationEntry entry) {
        return new PaperNode(
                entry.paperId(),
                entry.title(),
                entry.year() != null ? entry.year() : 0,
                null
        );
    }
}

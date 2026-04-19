package com.paperlineage.graph;

import com.paperlineage.ingestion.CitationEntry;
import com.paperlineage.ingestion.CitationGraph;
import com.paperlineage.ingestion.HfModelInfo;
import com.paperlineage.ingestion.PaperMetadata;
import com.paperlineage.ingestion.PwcData;
import com.paperlineage.ingestion.RepoResult;
import com.paperlineage.ingestion.RunnabilityScore;
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

    public record ScoredRepo(RepoResult repo, RunnabilityScore score) {}

    @Transactional
    public PaperNode write(PaperMetadata metadata,
                           CitationGraph citations,
                           List<ScoredRepo> repos,
                           PwcData pwcData,
                           List<HfModelInfo> hfModels) {

        log.info("Writing paper {} to Neo4j tasks={} hfModels={}",
                metadata.arxivId(), pwcData.tasks().size(), hfModels.size());

        List<AuthorNode> authors = metadata.authors().stream()
                .map(AuthorNode::new)
                .toList();

        List<RepoNode> repoNodes = repos.stream()
                .map(sr -> new RepoNode(
                        sr.repo().fullName(), sr.repo().url(),
                        sr.repo().description(), sr.repo().language(),
                        sr.repo().stars(),
                        sr.score().total(), sr.score().label(),
                        sr.score().hasCi(), sr.score().hasDocker(),
                        sr.score().hasDeps(), sr.score().daysSinceCommit()))
                .toList();

        List<PaperNode> citationNodes = citations.backwardCitations().stream()
                .map(this::toCitationStub)
                .toList();

        String topHfModel = hfModels.isEmpty() ? null : hfModels.get(0).modelId();

        PaperNode paper = new PaperNode(
                metadata.arxivId(), metadata.title(), metadata.year(), metadata.abstractText(),
                pwcData.pwcId(),
                pwcData.tasks(),
                pwcData.methods(),
                hfModels.size(),
                topHfModel,
                authors, repoNodes, citationNodes
        );

        PaperNode saved = paperRepository.save(paper);
        log.info("Saved paper {} authors={} repos={} citations={} tasks={} hfModels={}",
                metadata.arxivId(), authors.size(), repoNodes.size(),
                citationNodes.size(), pwcData.tasks().size(), hfModels.size());
        return saved;
    }

    private PaperNode toCitationStub(CitationEntry entry) {
        return new PaperNode(entry.paperId(), entry.title(),
                entry.year() != null ? entry.year() : 0, null);
    }
}

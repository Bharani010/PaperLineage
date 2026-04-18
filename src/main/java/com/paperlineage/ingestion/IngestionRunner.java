package com.paperlineage.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("cli")
public class IngestionRunner implements CommandLineRunner {

    private final ArxivClient arxivClient;
    private final PdfParser pdfParser;
    private final ObjectMapper objectMapper;

    public IngestionRunner(ArxivClient arxivClient, PdfParser pdfParser, ObjectMapper objectMapper) {
        this.arxivClient = arxivClient;
        this.pdfParser = pdfParser;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        String arxivId = null;
        for (int i = 0; i < args.length - 1; i++) {
            if ("--arxiv-id".equals(args[i])) {
                arxivId = args[i + 1];
                break;
            }
        }
        if (arxivId == null) {
            return;
        }

        PaperMetadata meta = arxivClient.fetch(arxivId);
        meta = pdfParser.enrich(meta);
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(meta));
    }
}

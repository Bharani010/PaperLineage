package com.paperlineage.ingestion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PdfParser {

    private static final Logger log = LoggerFactory.getLogger(PdfParser.class);

    private static final Pattern ABSTRACT_PATTERN = Pattern.compile(
            "(?i)abstract[\\s\\n]+(.+?)(?=\\n\\s*(?:\\d+\\.?\\s+)?introduction|\\n\\s*keywords)",
            Pattern.DOTALL);
    private static final Pattern METHODS_PATTERN = Pattern.compile(
            "(?i)\\n\\s*(?:\\d+\\.?\\s+)?(?:method(?:s|ology)?|approach|our approach)[\\s\\n]+(.+?)(?=\\n\\s*\\d+\\.\\s+\\w|\\z)",
            Pattern.DOTALL);

    private final WebClient webClient;

    public PdfParser(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    public PaperMetadata enrich(PaperMetadata meta) {
        try {
            log.info("Downloading PDF for {}", meta.arxivId());
            byte[] pdfBytes = webClient.get()
                    .uri(meta.pdfUrl())
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            String fullText;
            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                fullText = new PDFTextStripper().getText(doc);
            }

            String abstractText = extract(ABSTRACT_PATTERN, fullText, meta.abstractText());
            String methodsText = extract(METHODS_PATTERN, fullText, "");

            return new PaperMetadata(
                    meta.arxivId(), meta.title(), meta.authors(), meta.year(),
                    abstractText, methodsText, meta.pdfUrl());
        } catch (Exception e) {
            log.warn("PDF enrichment failed for {}: {}", meta.arxivId(), e.getMessage());
            return meta;
        }
    }

    private String extract(Pattern pattern, String text, String fallback) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).strip() : fallback;
    }
}

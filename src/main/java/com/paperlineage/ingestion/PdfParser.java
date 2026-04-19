package com.paperlineage.ingestion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    // Matches github.com/owner/repo — handles URLs with or without https:// prefix,
    // with possible trailing punctuation (period, comma, paren) that's not part of the path
    private static final Pattern GITHUB_URL_PATTERN = Pattern.compile(
            "github\\.com/([A-Za-z0-9][A-Za-z0-9_-]{0,38}/[A-Za-z0-9][A-Za-z0-9_.\\-]{0,99})",
            Pattern.CASE_INSENSITIVE);

    // Org-level pages and non-repo paths to filter out
    private static final Set<String> SKIP_OWNERS = Set.of(
            "features", "about", "pricing", "marketplace", "login", "join",
            "organizations", "apps", "sponsors", "explore", "topics", "trending");

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
            String firstPageText;
            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                fullText = new PDFTextStripper().getText(doc);

                // Extract first page separately — GitHub links are almost always there
                PDFTextStripper firstPageStripper = new PDFTextStripper();
                firstPageStripper.setStartPage(1);
                firstPageStripper.setEndPage(Math.min(2, doc.getNumberOfPages()));
                firstPageText = firstPageStripper.getText(doc);
            }

            String abstractText = extract(ABSTRACT_PATTERN, fullText, meta.abstractText());
            String methodsText  = extract(METHODS_PATTERN, fullText, "");

            // Extract GitHub repos: first page gets priority, then full text
            List<String> directRepos = extractGithubRepos(firstPageText, fullText);
            log.info("PDF GitHub repos found for {}: {}", meta.arxivId(), directRepos);

            return new PaperMetadata(
                    meta.arxivId(), meta.title(), meta.authors(), meta.year(),
                    abstractText, methodsText, meta.pdfUrl(), directRepos);

        } catch (Exception e) {
            log.warn("PDF enrichment failed for {}: {}", meta.arxivId(), e.getMessage());
            return new PaperMetadata(
                    meta.arxivId(), meta.title(), meta.authors(), meta.year(),
                    meta.abstractText(), meta.methodsText(), meta.pdfUrl(), List.of());
        }
    }

    private List<String> extractGithubRepos(String firstPage, String fullText) {
        Set<String> seen = new LinkedHashSet<>();

        // Scan first page first (highest signal), then rest of document
        for (String text : new String[]{firstPage, fullText}) {
            Matcher m = GITHUB_URL_PATTERN.matcher(text);
            while (m.find()) {
                String raw = m.group(1);
                // Strip trailing punctuation that PDFBox may include after URLs
                raw = raw.replaceAll("[.,;:)>\"']+$", "");
                String[] parts = raw.split("/");
                if (parts.length < 2) continue;
                String owner = parts[0];
                String repo  = parts[1].replaceAll("[^A-Za-z0-9_.\\-]", "");
                if (repo.isBlank() || SKIP_OWNERS.contains(owner.toLowerCase())) continue;
                seen.add(owner + "/" + repo);
                if (seen.size() >= 5) break; // cap at 5 direct links
            }
            if (seen.size() >= 5) break;
        }

        return new ArrayList<>(seen);
    }

    private String extract(Pattern pattern, String text, String fallback) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).strip() : fallback;
    }
}

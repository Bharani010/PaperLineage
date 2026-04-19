package com.paperlineage.ingestion;

import java.util.List;

public record RunGuide(
        String difficulty,
        String estimatedTime,
        List<String> prerequisites,
        List<Step> steps,
        List<String> commonIssues
) {
    public record Step(String title, String command, String notes) {}
}

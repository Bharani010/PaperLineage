package com.paperlineage.ingestion;

/**
 * Raw signals fetched from the GitHub API for a single repository.
 * All fields are collected in parallel; missing/errored fields fall back to safe defaults.
 */
public record RepoSignals(
        // ── Setup / environment files ─────────────────────────
        boolean hasDockerfile,
        boolean hasDockerCompose,
        boolean hasCondaEnv,      // environment.yml
        boolean hasRequirements,  // requirements.txt
        boolean hasSetupPy,       // setup.py
        boolean hasPyproject,     // pyproject.toml
        boolean hasPackageJson,   // package.json

        // ── CI ────────────────────────────────────────────────
        CiState ciState,

        // ── Maintenance ───────────────────────────────────────
        long daysSinceLastCommit,

        // ── Community ─────────────────────────────────────────
        int stars,
        int openIssuesCount
) {
    public enum CiState { PASSING, FAILING, NONE, UNKNOWN }

    /** Returned when all API calls fail — penalises unknown repos fairly. */
    public static RepoSignals unknown(int stars, int openIssues) {
        return new RepoSignals(
                false, false, false, false, false, false, false,
                CiState.UNKNOWN, 730, stars, openIssues
        );
    }
}

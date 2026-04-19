package com.paperlineage.ingestion;

/**
 * Computed 0-100 runnability score with per-signal breakdown.
 *
 * Scoring dimensions:
 *   ciScore       0-25   CI passing on latest run
 *   setupScore    0-20   Dockerfile / conda environment present
 *   depsScore     0-15   requirements.txt / setup.py / pyproject.toml
 *   recencyScore  0-20   Days since last commit
 *   issueScore    0-10   Open-issue ratio vs. stars
 *   starScore     0-10   Community validation via star count
 *   ─────────────────
 *   total         0-100
 */
public record RunnabilityScore(
        int total,
        int ciScore,
        int setupScore,
        int depsScore,
        int recencyScore,
        int issueScore,
        int starScore,
        String label,          // "Run it" | "Risky" | "Don't bother"
        boolean hasCi,
        boolean hasDocker,
        boolean hasDeps,
        int daysSinceCommit
) {
    public static String labelFor(int total) {
        if (total >= 71) return "Run it";
        if (total >= 41) return "Risky";
        return "Don't bother";
    }

    public static RunnabilityScore zero() {
        return new RunnabilityScore(0, 0, 0, 0, 0, 0, 0, "Don't bother", false, false, false, 9999);
    }
}

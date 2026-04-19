package com.paperlineage.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.stream.StreamSupport;

/**
 * Scores a GitHub repository on actual runnability using six independent signals
 * fetched from the GitHub API in parallel (3 calls per repo).
 */
@Service
public class ReproducibilityScorer {

    private static final Logger log = LoggerFactory.getLogger(ReproducibilityScorer.class);
    private static final String BASE = "https://api.github.com";
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(6);

    private static final Set<String> SETUP_FILES = Set.of(
            "dockerfile", "docker-compose.yml", "docker-compose.yaml"
    );
    private static final Set<String> CONDA_FILES = Set.of("environment.yml", "environment.yaml");
    private static final Set<String> DEP_FILES = Set.of(
            "requirements.txt", "setup.py", "pyproject.toml", "package.json",
            "gemfile", "go.mod", "cargo.toml"
    );

    private final WebClient webClient;
    private final String githubToken;

    public ReproducibilityScorer(WebClient.Builder builder,
                                 @Value("${GITHUB_TOKEN:}") String githubToken) {
        this.webClient = builder.baseUrl(BASE).build();
        this.githubToken = githubToken;
    }

    // ── Public API ───────────────────────────────────────────

    public RunnabilityScore score(RepoResult repo) {
        String[] parts = repo.fullName().split("/", 2);
        if (parts.length < 2) {
            log.warn("Invalid repo fullName: {}", repo.fullName());
            return RunnabilityScore.zero();
        }
        String owner = parts[0];
        String name  = parts[1];

        // Fan out 3 independent API calls in parallel
        Mono<JsonNode> contentsMono  = fetchContents(owner, name);
        Mono<JsonNode> ciMono        = fetchLatestCiRun(owner, name);
        Mono<JsonNode> commitsMono   = fetchLatestCommit(owner, name);

        RepoSignals signals = Mono.zip(contentsMono, ciMono, commitsMono)
                .map(t -> buildSignals(t.getT1(), t.getT2(), t.getT3(),
                        repo.stars(), repo.openIssuesCount()))
                .onErrorReturn(RepoSignals.unknown(repo.stars(), repo.openIssuesCount()))
                .blockOptional(Duration.ofSeconds(20))
                .orElseGet(() -> RepoSignals.unknown(repo.stars(), repo.openIssuesCount()));

        RunnabilityScore result = compute(signals);
        log.info("Runnability {}/{}: {}/100 [{}] ci={} docker={} deps={} days={}",
                owner, name, result.total(), result.label(),
                signals.ciState(), result.hasDocker(), result.hasDeps(), result.daysSinceCommit());
        return result;
    }

    // ── GitHub API calls ─────────────────────────────────────

    private Mono<JsonNode> fetchContents(String owner, String name) {
        return get("/repos/" + owner + "/" + name + "/contents/")
                .onErrorReturn(nullNode());
    }

    private Mono<JsonNode> fetchLatestCiRun(String owner, String name) {
        return get("/repos/" + owner + "/" + name + "/actions/runs?per_page=1")
                .onErrorReturn(nullNode());
    }

    private Mono<JsonNode> fetchLatestCommit(String owner, String name) {
        return get("/repos/" + owner + "/" + name + "/commits?per_page=1")
                .onErrorReturn(nullNode());
    }

    private Mono<JsonNode> get(String path) {
        return webClient.get()
                .uri(path)
                .headers(h -> {
                    h.set("Accept", "application/vnd.github+json");
                    h.set("X-GitHub-Api-Version", "2022-11-28");
                    if (githubToken != null && !githubToken.isBlank()) {
                        h.set("Authorization", "Bearer " + githubToken);
                    }
                })
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(CALL_TIMEOUT)
                .onErrorReturn(nullNode());
    }

    // ── Signal extraction ────────────────────────────────────

    private RepoSignals buildSignals(JsonNode contents, JsonNode ciRuns,
                                     JsonNode commits, int stars, int openIssues) {
        // --- contents ---
        boolean hasDockerfile    = false;
        boolean hasDockerCompose = false;
        boolean hasCondaEnv      = false;
        boolean hasRequirements  = false;
        boolean hasSetupPy       = false;
        boolean hasPyproject     = false;
        boolean hasPackageJson   = false;

        if (contents.isArray()) {
            for (JsonNode entry : contents) {
                String fname = entry.path("name").asText("").toLowerCase(Locale.ROOT);
                if (fname.equals("dockerfile"))                   hasDockerfile    = true;
                if (SETUP_FILES.contains(fname) && fname.contains("compose")) hasDockerCompose = true;
                if (CONDA_FILES.contains(fname))                  hasCondaEnv      = true;
                if (fname.equals("requirements.txt"))             hasRequirements  = true;
                if (fname.equals("setup.py"))                     hasSetupPy       = true;
                if (fname.equals("pyproject.toml"))               hasPyproject     = true;
                if (fname.equals("package.json"))                 hasPackageJson   = true;
            }
        }

        // --- CI ---
        RepoSignals.CiState ciState = RepoSignals.CiState.NONE;
        JsonNode runs = ciRuns.path("workflow_runs");
        if (runs.isArray() && runs.size() > 0) {
            String conclusion = runs.get(0).path("conclusion").asText("");
            ciState = switch (conclusion) {
                case "success"  -> RepoSignals.CiState.PASSING;
                case "failure",
                     "timed_out",
                     "startup_failure" -> RepoSignals.CiState.FAILING;
                default -> RepoSignals.CiState.NONE; // in_progress / cancelled / no runs
            };
        }

        // --- commit recency ---
        long daysSince = 730; // default: 2 years (penalised)
        if (commits.isArray() && commits.size() > 0) {
            String dateStr = commits.get(0).path("commit").path("author").path("date").asText("");
            if (!dateStr.isBlank()) {
                try {
                    Instant commitTime = Instant.parse(dateStr);
                    daysSince = Duration.between(commitTime, Instant.now()).toDays();
                } catch (Exception ignored) {}
            }
        }

        return new RepoSignals(
                hasDockerfile, hasDockerCompose, hasCondaEnv,
                hasRequirements, hasSetupPy, hasPyproject, hasPackageJson,
                ciState, daysSince, stars, openIssues
        );
    }

    // ── Score computation ────────────────────────────────────

    private RunnabilityScore compute(RepoSignals s) {
        int ci       = ciScore(s);
        int setup    = setupScore(s);
        int deps     = depsScore(s);
        int recency  = recencyScore(s);
        int issues   = issueScore(s);
        int stars    = starScore(s);
        int total    = ci + setup + deps + recency + issues + stars;

        return new RunnabilityScore(
                total, ci, setup, deps, recency, issues, stars,
                RunnabilityScore.labelFor(total),
                s.ciState() == RepoSignals.CiState.PASSING,
                s.hasDockerfile() || s.hasDockerCompose() || s.hasCondaEnv(),
                s.hasRequirements() || s.hasSetupPy() || s.hasPyproject() || s.hasPackageJson(),
                (int) s.daysSinceLastCommit()
        );
    }

    private int ciScore(RepoSignals s) {
        return switch (s.ciState()) {
            case PASSING  -> 25;
            case FAILING  -> 5;
            case NONE     -> 5;  // no CI is penalised but not zero — some great repos skip CI
            case UNKNOWN  -> 0;
        };
    }

    private int setupScore(RepoSignals s) {
        int score = 0;
        if (s.hasDockerfile())    score += 15;
        if (s.hasDockerCompose()) score += 5;
        if (s.hasCondaEnv())      score += 8;
        return Math.min(20, score);
    }

    private int depsScore(RepoSignals s) {
        if (s.hasRequirements())                  return 15;
        if (s.hasSetupPy() || s.hasPyproject())   return 12;
        if (s.hasPackageJson())                   return 8;
        return 0;
    }

    private int recencyScore(RepoSignals s) {
        long days = s.daysSinceLastCommit();
        if (days <=  90) return 20;
        if (days <= 180) return 15;
        if (days <= 365) return 10;
        if (days <= 730) return 5;
        return 0;
    }

    private int issueScore(RepoSignals s) {
        double ratio = (double) s.openIssuesCount() / (s.stars() + 1.0);
        if (ratio <= 0.02) return 10;
        if (ratio <= 0.05) return 7;
        if (ratio <= 0.10) return 4;
        return 0;
    }

    private int starScore(RepoSignals s) {
        int stars = s.stars();
        if (stars >= 500) return 10;
        if (stars >= 100) return 7;
        if (stars >= 50)  return 4;
        return 0;
    }

    private static JsonNode nullNode() {
        return com.fasterxml.jackson.databind.node.NullNode.getInstance();
    }
}

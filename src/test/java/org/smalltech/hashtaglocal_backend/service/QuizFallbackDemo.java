package org.smalltech.hashtaglocal_backend.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.service.QuizGenerationService.GeneratedQuiz;
import org.smalltech.hashtaglocal_backend.service.ai.AiFallbackChain;
import org.smalltech.hashtaglocal_backend.service.ai.AiProvider;
import org.smalltech.hashtaglocal_backend.service.ai.AnthropicProvider;
import org.smalltech.hashtaglocal_backend.service.ai.GeminiProvider;
import org.smalltech.hashtaglocal_backend.service.ai.GroqProvider;
import org.smalltech.hashtaglocal_backend.service.ai.QuizAiProperties;
import org.springframework.web.client.RestClient;

/**
 * Live team demo of quiz automation, run with {@code ./gradlew quizDemo}. Part 1 shows each
 * fallback case with real API calls; deliberately broken keys and models trigger the fallbacks, so
 * only one real Gemini key is needed. Part 2 generates quizzes for a few places through the real
 * chain and quality checks, and writes an HTML report to review together.
 *
 * <p>Not a test: it makes real API calls and is never run by {@code ./gradlew test} or CI. Needs no
 * database, admin token or running app. Keys come from .env and are never printed.
 */
public final class QuizFallbackDemo {

  private static final String DEFAULT_GEMINI_MODEL = "gemini-3.1-flash-lite";
  private static final String DEFAULT_ANTHROPIC_MODELS = "claude-sonnet-5-5,claude-haiku-5-5";
  private static final String DEFAULT_PLACES =
      "Port Blair:portblair;Koramangala, Bengaluru:koramangala;Shillong:shillong";

  // Clearly fake values: they make the real providers answer "invalid key" / "no such model".
  private static final String BROKEN_GEMINI_KEY = "AIzaSy-DEMO-INVALID-KEY-000000000000";
  private static final String BROKEN_CLAUDE_KEY = "sk-ant-DEMO-INVALID-KEY";
  private static final String BROKEN_GROQ_KEY = "gsk_DEMO_INVALID_KEY";
  private static final String RETIRED_MODEL = "gemini-demo-retired-model";

  private static final Path REPORT = Path.of("build", "quiz-demo", "quiz-report.html");

  private final ObjectMapper mapper = new ObjectMapper();
  private final List<AiProvider> providers =
      List.of(
          new AnthropicProvider(RestClient.builder(), mapper),
          new GroqProvider(RestClient.builder(), mapper),
          new GeminiProvider(RestClient.builder(), mapper));
  private final boolean pause;
  private final BufferedReader stdin =
      new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
  private final List<ScenarioRow> scenarioRows = new ArrayList<>();
  private final List<QuizCard> cards = new ArrayList<>();

  private record ScenarioRow(String title, String expected, String result, long millis) {}

  private record QuizCard(String place, GeneratedQuiz quiz, long millis) {}

  private QuizFallbackDemo(boolean pause) {
    this.pause = pause;
  }

  public static void main(String[] args) throws Exception {
    // Gradle's --args splits on spaces, so rejoin them: place names like "Port Blair" have spaces.
    String joined = String.join(" ", args);
    boolean pause = joined.contains("--pause");
    Matcher placesArg = Pattern.compile("--places=(.*?)(?=\\s--|$)").matcher(joined);
    String places = placesArg.find() ? placesArg.group(1) : DEFAULT_PLACES;
    showOnlyChainLogs();
    new QuizFallbackDemo(pause).run(parsePlaces(places));
  }

  private void run(List<Locality> places) throws Exception {
    List<String> geminiKeys = list(env("QUIZ_GEMINI_KEYS", ""));
    if (geminiKeys.isEmpty()) {
      System.out.println("QUIZ_GEMINI_KEYS is empty. Add a real Gemini key to .env and run again.");
      System.exit(1);
    }
    String goodKey = geminiKeys.get(0);
    String model =
        list(env("QUIZ_GEMINI_MODELS", DEFAULT_GEMINI_MODEL)).stream()
            .findFirst()
            .orElse(DEFAULT_GEMINI_MODEL);
    Locality demoPlace = places.get(0);

    title("PART 1 - FALLBACK MECHANISM (live API calls)");
    say("Order the chain always follows: Claude -> Groq -> Gemini.");
    say("Inside a provider: best model on every key first, then the next model.");
    say("Lines starting with '|' are the backend's own logs.");
    say("Keys are shown as #1, #2 (their position), never the key itself.");

    scenario(
        "1. Normal day",
        "Gemini answers on the first try.",
        chain(level("gemini", model, goodKey)),
        demoPlace,
        1);

    scenario(
        "2. A key is broken",
        "Key #1 is invalid -> AUTH_FAILED -> cooled down -> key #2 answers."
            + " On the 2nd quiz, key #1 is skipped without any call.",
        chain(level("gemini", model, BROKEN_GEMINI_KEY + "," + goodKey)),
        demoPlace,
        2);

    scenario(
        "3. A model is retired",
        "First model does not exist -> MODEL_UNAVAILABLE -> next model answers.",
        chain(level("gemini", RETIRED_MODEL + "," + model, goodKey)),
        demoPlace,
        1);

    scenario(
        "4. Claude and Groq are both down",
        "Claude fails -> Groq fails -> Gemini answers. All 3 levels in one request.",
        chain(
            level("anthropic", "claude-sonnet-5-5", BROKEN_CLAUDE_KEY),
            level("groq", "demo-model", BROKEN_GROQ_KEY),
            level("gemini", model, goodKey)),
        demoPlace,
        1);

    scenario(
        "5. Everything is down",
        "Every slot fails -> no quiz is saved, nothing crashes, admin can retry later.",
        chain(level("gemini", model, BROKEN_GEMINI_KEY)),
        demoPlace,
        1);

    title("PART 2 - QUIZ QUALITY (the real chain from .env)");
    say("Each quiz below already passed the automatic checks: valid JSON, 4 distinct");
    say("options, a valid answer, and not a repeat. Facts still need a human review.");
    AiFallbackChain realChain = chain(realLevels(model));
    QuizGenerationService service = new QuizGenerationService(realChain, null, mapper);
    for (Locality place : places) {
      waitForPresenter();
      long start = System.currentTimeMillis();
      Optional<GeneratedQuiz> quiz = service.generateCandidate(place, List.of());
      long millis = System.currentTimeMillis() - start;
      cards.add(new QuizCard(place.getName(), quiz.orElse(null), millis));
      printQuiz(place, quiz, millis);
    }

    writeReport();
    title("DONE");
    say("Open this file in a browser to review the quizzes with the team:");
    say(REPORT.toAbsolutePath().toString());
  }

  // ── Scenarios ─────────────────────────────────────────────────────────────

  private void scenario(
      String name, String expected, AiFallbackChain chain, Locality place, int quizzes)
      throws Exception {
    waitForPresenter();
    System.out.println();
    System.out.println("------------------------------------------------------------------");
    System.out.println(" SCENARIO " + name);
    System.out.println(" Expected: " + expected);
    System.out.println("------------------------------------------------------------------");
    QuizGenerationService service = new QuizGenerationService(chain, null, mapper);
    for (int i = 1; i <= quizzes; i++) {
      if (quizzes > 1) {
        System.out.println(" Quiz " + i + ":");
      }
      long start = System.currentTimeMillis();
      Optional<GeneratedQuiz> quiz = service.generateCandidate(place, List.of());
      long millis = System.currentTimeMillis() - start;
      String result =
          quiz.map(q -> "Quiz made by " + q.generatedBy())
              .orElse("No quiz (nothing saved, no crash)");
      System.out.println(" RESULT: " + result + "  [" + millis + " ms]");
      scenarioRows.add(
          new ScenarioRow(
              name + (quizzes > 1 ? " (quiz " + i + ")" : ""), expected, result, millis));
    }
  }

  private void printQuiz(Locality place, Optional<GeneratedQuiz> quiz, long millis) {
    System.out.println();
    System.out.println("== " + place.getName() + " (#" + place.getHashtag() + ") ==");
    if (quiz.isEmpty()) {
      System.out.println("   No valid quiz after 2 attempts.");
      return;
    }
    GeneratedQuiz q = quiz.get();
    System.out.println("   Q: " + q.question());
    for (int i = 0; i < q.options().size(); i++) {
      String mark = i + 1 == q.answerOptionIndex() ? " <-- correct" : "";
      System.out.println("   " + (i + 1) + ". " + q.options().get(i) + mark);
    }
    System.out.println("   Why: " + q.explanation());
    System.out.println("   Made by " + q.generatedBy() + " in " + millis + " ms");
  }

  // ── Chain building ────────────────────────────────────────────────────────

  private AiFallbackChain chain(QuizAiProperties.Level... levels) {
    return chain(List.of(levels));
  }

  private AiFallbackChain chain(List<QuizAiProperties.Level> levels) {
    QuizAiProperties properties = new QuizAiProperties();
    properties.setChain(new ArrayList<>(levels));
    return new AiFallbackChain(properties, providers);
  }

  /** The same chain the app builds from .env (mirrors application.yaml defaults). */
  private static List<QuizAiProperties.Level> realLevels(String geminiModel) {
    String claudeKeys =
        System.getenv().containsKey("QUIZ_ANTHROPIC_KEYS")
            ? System.getenv("QUIZ_ANTHROPIC_KEYS")
            : env("ANTHROPIC_API_KEY", "");
    return List.of(
        level("anthropic", env("QUIZ_ANTHROPIC_MODELS", DEFAULT_ANTHROPIC_MODELS), claudeKeys),
        level("groq", env("QUIZ_GROQ_MODELS", ""), env("QUIZ_GROQ_KEYS", "")),
        level("gemini", env("QUIZ_GEMINI_MODELS", geminiModel), env("QUIZ_GEMINI_KEYS", "")));
  }

  private static QuizAiProperties.Level level(String provider, String models, String keys) {
    QuizAiProperties.Level level = new QuizAiProperties.Level();
    level.setProvider(provider);
    level.setModels(list(models));
    level.setKeys(list(keys));
    return level;
  }

  // ── HTML report ───────────────────────────────────────────────────────────

  private void writeReport() throws Exception {
    StringBuilder html = new StringBuilder();
    html.append(
        """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Quiz Automation Demo</title>
        <style>
          body{font-family:system-ui,sans-serif;max-width:900px;margin:32px auto;padding:0 16px;
               color:#1f2328;background:#f6f8fa}
          h1{margin-bottom:4px} .sub{color:#59636e;margin-top:0}
          table{width:100%;border-collapse:collapse;background:#fff;border-radius:8px;overflow:hidden}
          th,td{text-align:left;padding:10px;border-bottom:1px solid #d1d9e0;vertical-align:top;
                font-size:14px}
          th{background:#eef1f4}
          .ok{color:#1a7f37;font-weight:600} .none{color:#cf222e;font-weight:600}
          .card{background:#fff;border:1px solid #d1d9e0;border-radius:8px;padding:16px 20px;
                margin:16px 0}
          .place{font-size:13px;color:#59636e;text-transform:uppercase;letter-spacing:.04em}
          .q{font-size:18px;font-weight:600;margin:6px 0 12px}
          ol{padding-left:22px;margin:0} li{padding:4px 0}
          li.correct{font-weight:600;color:#1a7f37} li.correct::after{content:"  \\2713 correct"}
          .why{background:#f6f8fa;border-left:3px solid #8250df;padding:8px 12px;margin-top:12px}
          .meta{font-size:12px;color:#59636e;margin-top:10px}
          .check{font-size:13px;color:#59636e;margin-top:10px}
        </style></head><body>
        <h1>Quiz Automation Demo</h1>
        """);
    html.append("<p class=\"sub\">Generated ")
        .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")))
        .append(" &middot; fallback order: Claude &rarr; Groq &rarr; Gemini</p>");

    html.append("<h2>1. Fallback mechanism</h2><table><tr><th>Scenario</th><th>Expected</th>")
        .append("<th>Result</th><th>Time</th></tr>");
    for (ScenarioRow row : scenarioRows) {
      boolean made = row.result().startsWith("Quiz made");
      html.append("<tr><td>")
          .append(esc(row.title()))
          .append("</td><td>")
          .append(esc(row.expected()))
          .append("</td><td class=\"")
          .append(made ? "ok" : "none")
          .append("\">")
          .append(esc(row.result()))
          .append("</td><td>")
          .append(row.millis())
          .append(" ms</td></tr>");
    }
    html.append("</table>");

    html.append("<h2>2. Quiz quality</h2>")
        .append("<p>Every quiz here passed the automatic checks. Review each one for: ")
        .append("correct answer, about this place, clear question, believable wrong options.</p>");
    for (QuizCard card : cards) {
      html.append("<div class=\"card\"><div class=\"place\">").append(esc(card.place()));
      if (card.quiz() == null) {
        html.append("</div><p class=\"none\">No valid quiz after 2 attempts.</p></div>");
        continue;
      }
      GeneratedQuiz q = card.quiz();
      html.append("</div><div class=\"q\">").append(esc(q.question())).append("</div><ol>");
      for (int i = 0; i < q.options().size(); i++) {
        html.append(i + 1 == q.answerOptionIndex() ? "<li class=\"correct\">" : "<li>")
            .append(esc(q.options().get(i)))
            .append("</li>");
      }
      html.append("</ol><div class=\"why\">")
          .append(esc(q.explanation()))
          .append("</div><div class=\"meta\">Made by ")
          .append(esc(q.generatedBy()))
          .append(" in ")
          .append(card.millis())
          .append(" ms</div><div class=\"check\">Review: &#9744; answer correct &nbsp;")
          .append("&#9744; local &nbsp;&#9744; clear &nbsp;&#9744; good options</div></div>");
    }
    html.append("</body></html>");

    Files.createDirectories(REPORT.getParent());
    Files.writeString(REPORT, html.toString(), StandardCharsets.UTF_8);
  }

  private static String esc(String text) {
    return text == null
        ? ""
        : text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private void waitForPresenter() throws Exception {
    if (pause) {
      System.out.println();
      System.out.print(">> Press Enter to continue...");
      System.out.flush();
      stdin.readLine();
    } else {
      // Small gap so free-tier per-minute limits are not hit during the demo.
      Thread.sleep(1500);
    }
  }

  /** Shows only the AI chain's own log lines, in a short format, so the demo output stays clean. */
  private static void showOnlyChainLogs() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    context.reset();
    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(context);
    encoder.setPattern("   | %msg%n");
    encoder.start();
    ConsoleAppender<ILoggingEvent> console = new ConsoleAppender<>();
    console.setContext(context);
    console.setEncoder(encoder);
    console.start();
    context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(Level.OFF);
    context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(console);
    context.getLogger("org.smalltech.hashtaglocal_backend.service.ai").setLevel(Level.INFO);
    context
        .getLogger("org.smalltech.hashtaglocal_backend.service.QuizGenerationService")
        .setLevel(Level.INFO);
  }

  private static List<Locality> parsePlaces(String spec) {
    List<Locality> places = new ArrayList<>();
    long id = 1;
    for (String entry : spec.split(";")) {
      String[] parts = entry.split(":", 2);
      if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
        places.add(
            Locality.builder().id(id++).name(parts[0].trim()).hashtag(parts[1].trim()).build());
      }
    }
    if (places.isEmpty()) {
      throw new IllegalArgumentException("Use --places=Name:hashtag;Name:hashtag");
    }
    return places;
  }

  private static void title(String text) {
    System.out.println();
    System.out.println("==================================================================");
    System.out.println(" " + text);
    System.out.println("==================================================================");
  }

  private static void say(String text) {
    System.out.println(" " + text);
  }

  private static String env(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static List<String> list(String csv) {
    return csv == null
        ? List.of()
        : Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }
}

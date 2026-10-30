package com.jenkinschina.atom.robotreport;

import com.jenkinschina.atom.sdk.AtomContext;
import com.jenkinschina.atom.sdk.AtomResult;
import com.jenkinschina.atom.sdk.AtomTask;
import com.jenkinschina.atom.sdk.ErrorType;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * robot as an atom: read Robot Framework's output.xml and put the totals on the build page.
 *
 * <p>Robot writes a rich log.html of its own; what a Jenkins build needs is the summary line and a link, so the atom
 * reads the statistics block and archives the log directory.
 */
public class RobotReportAtom implements AtomTask {

    @Override
    public AtomResult run(AtomContext context) throws Exception {
        Path workspace = context.getWorkspace();
        String pattern = context.getParam("pattern", "**/output.xml");
        String label = context.getParam("label", "Robot Framework");
        String outDir = context.getParam("outputDir", "out/robot");
        boolean failOnFailure = context.getBooleanParam("failOnFailure", false);
        boolean skipMarkingUnstable = context.getBooleanParam("skipMarkingUnstable", false);

        List<Path> files = match(workspace, pattern);
        if (files.isEmpty()) {
            return AtomResult.error(ErrorType.USER, 40001, "no robot output.xml matched " + pattern);
        }

        int passed = 0;
        int failed = 0;
        int skipped = 0;
        int total = 0;
        for (Path file : files) {
            Totals totals = read(file);
            passed += totals.passed;
            failed += totals.failed;
            skipped += totals.skipped;
            total += totals.total;
        }
        if (total == 0) {
            return AtomResult.error(ErrorType.USER, 40002, "the output.xml has no test statistics");
        }

        Path dir = workspace.resolve(outDir);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("index.html"),
                render(label, pattern, files.size(), total, passed, failed, skipped), StandardCharsets.UTF_8);

        context.log("[robot] " + total + " test(s): " + passed + " passed, " + failed + " failed, " + skipped
                + " skipped");

        AtomResult result = AtomResult.success()
                .data("total", String.valueOf(total))
                .data("passed", String.valueOf(passed))
                .data("failed", String.valueOf(failed))
                .data("skipped", String.valueOf(skipped))
                .report("report", label, dir.toString(), "index.html");
        if (failed > 0 && !skipMarkingUnstable) {
            result.markUnstable();
        }
        if (failed > 0 && failOnFailure) {
            return AtomResult.failure(failed + " test(s) failed")
                    .data("total", String.valueOf(total))
                    .data("failed", String.valueOf(failed))
                    .report("report", label, dir.toString(), "index.html");
        }
        return result;
    }

    /** {@code <statistics><total><stat pass="3" fail="1" skip="0">…}} */
    private static Totals read(Path file) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(file.toFile());
            NodeList totals = document.getElementsByTagName("total");
            Totals result = new Totals();
            if (totals.getLength() > 0) {
                Element total = (Element) totals.item(0);
                NodeList stats = total.getElementsByTagName("stat");
                for (int i = 0; i < stats.getLength(); i++) {
                    Element stat = (Element) stats.item(i);
                    result.passed += number(stat.getAttribute("pass"));
                    result.failed += number(stat.getAttribute("fail"));
                    result.skipped += number(stat.getAttribute("skip"));
                }
                result.total = result.passed + result.failed + result.skipped;
                return result;
            }
            // newer schema: <statistics><total><stat pass="…"/> under <suite><statistics>
            NodeList all = document.getElementsByTagName("stat");
            for (int i = 0; i < all.getLength(); i++) {
                Element stat = (Element) all.item(i);
                result.passed += number(stat.getAttribute("pass"));
                result.failed += number(stat.getAttribute("fail"));
                result.skipped += number(stat.getAttribute("skip"));
            }
            result.total = result.passed + result.failed + result.skipped;
            return result;
        } catch (Exception e) {
            return new Totals();
        }
    }

    private static int number(String value) {
        try {
            return value == null || value.isBlank() ? 0 : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<Path> match(Path workspace, String pattern) throws Exception {
        List<PathMatcher> matchers = new ArrayList<>();
        for (String part : pattern.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + trimmed));
            }
        }
        List<Path> files = new ArrayList<>();
        try (var walk = Files.walk(workspace, 12)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> matchers.stream().anyMatch(m -> m.matches(workspace.relativize(path))
                            || m.matches(java.nio.file.Path.of("_").resolve(workspace.relativize(path)))
                            || m.matches(path.getFileName())))
                    .sorted()
                    .limit(100)
                    .forEach(files::add);
        }
        return files;
    }

    private static String render(String label, String pattern, int files, int total, int passed, int failed,
                                 int skipped) {
        StringBuilder html = new StringBuilder("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">");
        html.append("<title>").append(label).append("</title><style>")
                .append("body{font-family:-apple-system,'Segoe UI',sans-serif;margin:0;padding:28px;background:#f6f7f9}")
                .append("h1{font-size:22px;margin:0 0 4px}p.meta{color:#6b7280;font-size:13px;margin:0 0 18px}")
                .append(".cards{display:flex;gap:10px}")
                .append(".card{background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:12px 16px;min-width:110px}")
                .append(".card b{display:block;font-size:22px}.card span{font-size:12px;color:#6b7280}")
                .append("</style></head><body>");
        html.append("<h1>").append(label).append("</h1><p class=\"meta\">").append(files)
                .append(" file(s), ").append(total).append(" test(s) · <code>").append(pattern)
                .append("</code></p><div class=\"cards\">");
        html.append("<div class=\"card\"><b>").append(passed).append("</b><span>passed</span></div>");
        html.append("<div class=\"card\"><b>").append(failed).append("</b><span>failed</span></div>");
        html.append("<div class=\"card\"><b>").append(skipped).append("</b><span>skipped</span></div>");
        return html.append("</div></body></html>").toString();
    }

    private static final class Totals {
        int total;
        int passed;
        int failed;
        int skipped;
    }
}

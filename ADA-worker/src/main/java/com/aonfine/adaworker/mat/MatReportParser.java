package com.aonfine.adaworker.mat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

/**
 * Deterministic parser for Eclipse MAT's batch HTML reports (System_Overview / Leak_Suspects).
 * Every number/name here is read out of MAT's own generated markup -- nothing is estimated or
 * invented. When an expected pattern is not found, the corresponding field is left null/empty
 * rather than guessed; callers must treat that as "not extractable" and say so in the report.
 */
public final class MatReportParser {

    private static final Pattern HOLDER_PATTERN = Pattern.compile(
            "The thread <strong>(.*?)</strong> keeps local variables with total size <strong>([\\d,]+) \\(([\\d.]+)%\\)</strong>",
            Pattern.DOTALL);
    private static final Pattern ACCUMULATION_PATTERN = Pattern.compile(
            "accumulated in one instance of <strong><q>(.*?)</q></strong>.*?occupies <strong>([\\d,]+) \\(([\\d.]+)%\\)</strong>",
            Pattern.DOTALL);
    private static final Pattern THREAD_ADDRESS_PATTERN = Pattern.compile("@\\s*(0x[0-9a-fA-F]+)\\s*(.*)$");

    /** Parses the "Heap Dump Overview" key/value table out of a System_Overview (or Leak_Suspects) report's index.html. */
    public HeapOverview parseHeapOverview(File indexHtml) throws IOException {
        Document doc = Jsoup.parse(indexHtml, "UTF-8");
        HeapOverview overview = new HeapOverview();
        Element heading = doc.selectFirst("h5:matchesOwn(^Heap Dump Overview)");
        if (heading == null) return overview;
        Element table = nextTable(heading);
        if (table == null) return overview;
        for (Element row : table.select("tbody > tr")) {
            Elements cells = row.select("> td");
            if (cells.size() < 2) continue;
            String key = cells.get(0).text().trim();
            if (key.isEmpty()) continue;
            String value = cells.get(1).text().trim();
            overview.fields.put(key, value);
        }
        return overview;
    }

    /** Parses every "Problem Suspect N" block out of a Leak_Suspects report's index.html. Does not follow stack-trace links. */
    public List<LeakSuspect> parseLeakSuspects(File indexHtml) throws IOException {
        Document doc = Jsoup.parse(indexHtml, "UTF-8");
        List<LeakSuspect> suspects = new ArrayList<>();
        for (Element h3 : doc.select("h3")) {
            String title = h3.text().trim();
            if (!title.startsWith("Problem Suspect")) continue;
            Element body = h3.nextElementSibling();
            if (body == null) continue;
            Element importantDiv = body.selectFirst("div.important");
            if (importantDiv == null) continue;
            suspects.add(parseOneSuspect(title, importantDiv));
        }
        return suspects;
    }

    private LeakSuspect parseOneSuspect(String title, Element importantDiv) {
        LeakSuspect suspect = new LeakSuspect();
        suspect.title = title;
        Element contentDiv = importantDiv.child(0); // first child div holds the description prose
        suspect.rawDescriptionText = contentDiv.text();
        String html = contentDiv.html();

        Matcher holder = HOLDER_PATTERN.matcher(html);
        if (holder.find()) {
            suspect.holderIdentity = unescape(stripTags(holder.group(1)));
            suspect.retainedBytes = parseLong(holder.group(2));
            suspect.retainedPercent = parseDouble(holder.group(3));
            Matcher addr = THREAD_ADDRESS_PATTERN.matcher(suspect.holderIdentity);
            if (addr.find()) {
                suspect.holderAddress = addr.group(1);
                suspect.threadName = addr.group(2).trim();
            }
        }
        Matcher accumulation = ACCUMULATION_PATTERN.matcher(html);
        if (accumulation.find()) {
            suspect.accumulationPointClass = unescape(stripTags(accumulation.group(1)));
            suspect.accumulationPointBytes = parseLong(accumulation.group(2));
            suspect.accumulationPointPercent = parseDouble(accumulation.group(3));
        }
        for (Element li : importantDiv.select("ul[title=Keywords] li")) {
            suspect.keywords.add(li.text());
        }
        Element sigHeading = importantDiv.selectFirst("p:matchesOwn(^Significant stack frames)");
        if (sigHeading != null) {
            Element list = sigHeading.nextElementSibling();
            if (list != null && "ul".equals(list.tagName())) {
                for (Element li : list.select("> li")) {
                    suspect.significantStackFrames.add(li.text());
                }
            }
        }
        for (Element a : importantDiv.select("a[href]")) {
            String text = a.text().toLowerCase();
            if (text.equals("see stacktrace")) suspect.stackTraceHref = a.attr("href");
            else if (text.contains("stacktrace with involved local variables")) suspect.stackTraceWithLocalsHref = a.attr("href");
            else if (text.startsWith("details")) suspect.detailsHref = a.attr("href");
        }
        return suspect;
    }

    /** Reads the real MAT-generated thread stack (verbatim, one frame per line) from a linked stack-trace page such as pages/24.html. */
    public List<String> parseStackTracePage(File stackHtml) throws IOException {
        Document doc = Jsoup.parse(stackHtml, "UTF-8");
        Element pre = doc.selectFirst("pre");
        List<String> lines = new ArrayList<>();
        if (pre == null) return lines;
        for (String line : pre.text().split("\\R")) {
            if (!line.trim().isEmpty()) lines.add(line);
        }
        return lines;
    }

    private static Element nextTable(Element heading) {
        Element sibling = heading;
        for (int i = 0; i < 3 && sibling != null; i++) {
            sibling = sibling.nextElementSibling();
            if (sibling == null) break;
            if ("table".equals(sibling.tagName())) return sibling;
            Element nested = sibling.selectFirst("table.result");
            if (nested != null) return nested;
        }
        return null;
    }

    private static String stripTags(String html) {
        return Jsoup.parse(html).text();
    }

    private static String unescape(String text) {
        return Parser.unescapeEntities(text, false);
    }

    private static Long parseLong(String withCommas) {
        try {
            return Long.parseLong(withCommas.replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return null;
        }
    }
}

package com.x8ing.mtl.server.gpsquality.diagnostics;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.ext.Locator2;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Streaming GPX review retaining observations and original XML, without a device-extension DOM.
 */
public final class GpxDocument {
    public static final String REPAIR_NAMESPACE = "https://mytraillog.com/xmlschemas/repair/v1";
    public static final int MAX_SOURCE_BYTES = 64 * 1024 * 1024;
    public static final int MAX_POINTS = 1_000_000;
    private static final int MAX_XML_DEPTH = 64;
    private static final int MAX_FIELD_CHARACTERS = 64 * 1024;
    // Bound retained review data, leaving room for source bytes and diagnosis on the 320 MiB heap.
    // Irrelevant XML nodes, comments and device extensions do not consume this budget.
    private static final long MAX_RETAINED_REVIEW_UNITS = 160L * 1024 * 1024;
    private static final int OBSERVATION_UNITS = 64;
    private static final int SECTION_UNITS = 128;
    private static final int NUMBER_UNITS = 24;
    private static final int INSTANT_UNITS = 24;
    private static final int STRING_UNITS = 48;
    private static final int PROBLEM_UNITS = 64;
    private static final Set<String> GPX_NAMESPACES = Set.of("http://www.topografix.com/GPX/1/0", "http://www.topografix.com/GPX/1/1");
    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private static final System.Logger LOG = System.getLogger(GpxDocument.class.getName());

    public record ParseProblem(int pointIndex, String field, String message) {
    }

    /**
     * A bounded review could not finish; this is not evidence of malformed source data.
     */
    public static final class ReviewLimitExceeded extends IllegalArgumentException {
        public ReviewLimitExceeded(String message) {
            super(message);
        }
    }

    public static final class ParseFailure extends IllegalArgumentException {
        public ParseFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final byte[] source;
    private final Charset encoding;
    private final String sourceHash;
    private final List<TrackObservation> points;
    private final List<TrackSection> sections;
    private final List<ParseProblem> problems;

    private GpxDocument(byte[] source, ObservationHandler handler) {
        this.source = source;
        encoding = handler.encoding;
        sourceHash = hash(source);
        points = List.copyOf(handler.points);
        List<TrackSection> recordingSections = new ArrayList<>();
        for (Section section : handler.sections) {
            int index = recordingSections.size();
            String name = section.recording.name;
            recordingSections.add(new TrackSection(index, name == null || name.isBlank()
                    ? "Recording " + (index + 1) : name, section.recording.route ? "ROUTE" : "TRACK",
                    section.first, section.count));
        }
        sections = List.copyOf(recordingSections);
        problems = List.copyOf(handler.problems);
    }

    public static GpxDocument parse(byte[] source) {
        Objects.requireNonNull(source, "Source is required.");
        if (source.length > MAX_SOURCE_BYTES) throw new ReviewLimitExceeded("Source exceeds the 64 MiB review limit.");
        try {
            byte[] original = source.clone();
            var factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            var parser = factory.newSAXParser();
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var handler = new ObservationHandler();
            parser.parse(new ByteArrayInputStream(original), handler);
            if (handler.assumedUtcTimes > 0) LOG.log(System.Logger.Level.INFO,
                    "GPX review timestamps without an offset interpreted as UTC: observations={0}", handler.assumedUtcTimes);
            return new GpxDocument(original, handler);
        } catch (ParseFailure | ReviewLimitExceeded e) {
            LOG.log(System.Logger.Level.WARNING, "GPX parsing failed: type={0}, message={1}", e.getClass().getSimpleName(), e.getMessage());
            throw e;
        } catch (Exception e) {
            Integer line = e instanceof SAXParseException sax ? sax.getLineNumber() : null;
            Integer column = e instanceof SAXParseException sax ? sax.getColumnNumber() : null;
            String message = "Could not parse GPX XML" + (line == null ? "." : " at line " + line + ", column " + column + ".");
            LOG.log(System.Logger.Level.WARNING, "GPX parsing failed: type={0}, message={1}", e.getClass().getSimpleName(), message);
            throw new ParseFailure(message, e);
        }
    }

    private static final class Recording {
        final boolean route;
        String name;
        boolean nameSeen;

        Recording(boolean route) {
            this.route = route;
        }
    }

    private static final class Section {
        final Recording recording;
        final int first;
        final int depth;
        int count;

        Section(Recording recording, int first, int depth) {
            this.recording = recording;
            this.first = first;
            this.depth = depth;
        }
    }

    private static final class Point {
        String latitude, longitude, time, elevation;
        boolean timeSeen, elevationSeen, estimated;

        Point(Attributes attributes) {
            latitude = attributes.getValue("lat");
            longitude = attributes.getValue("lon");
        }
    }

    private static final class ObservationHandler extends DefaultHandler2 {
        final List<TrackObservation> points = new ArrayList<>();
        final List<Section> sections = new ArrayList<>();
        final List<ParseProblem> problems = new ArrayList<>();
        Charset encoding = StandardCharsets.UTF_8;
        Locator locator;
        String namespace;
        int depth;
        Recording recording;
        Section section;
        Point point;
        String field;
        int fieldDepth;
        StringBuilder text;
        long retainedUnits;
        boolean warnedTime;
        int assumedUtcTimes;

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        private void reserve(long units) {
            retainedUnits += units;
            if (retainedUnits > MAX_RETAINED_REVIEW_UNITS)
                throw new ReviewLimitExceeded("Source exceeds the observation storage budget for review.");
        }

        private void capture(String field) {
            this.field = field;
            fieldDepth = depth;
            text = new StringBuilder();
            if (!"ele".equals(field)) reserve(STRING_UNITS);
        }

        @Override
        public void startElement(String uri, String local, String qualified, Attributes attributes) {
            if (++depth > MAX_XML_DEPTH)
                throw new ReviewLimitExceeded("Source exceeds the XML nesting limit for review.");
            if (depth == 1) {
                // The XML declaration has been processed by the first element, but not at startDocument.
                if (locator instanceof Locator2 information && information.getEncoding() != null)
                    encoding = Charset.forName(information.getEncoding());
                if (!"gpx".equals(local) || (!uri.isEmpty() && !GPX_NAMESPACES.contains(uri)))
                    throw new ParseFailure("Expected a GPX 1.0 or 1.1 document.", null);
                namespace = uri;
                return;
            }
            if (point != null && REPAIR_NAMESPACE.equals(uri) && "time".equals(local)) point.estimated = true;
            if (!uri.equals(namespace)) return;
            if (depth == 2 && ("trk".equals(local) || "rte".equals(local))) {
                recording = new Recording("rte".equals(local));
                reserve(SECTION_UNITS);
                if (recording.route) startSection();
            } else if (recording != null && !recording.route && depth == 3 && "trkseg".equals(local)) {
                startSection();
            } else if (recording != null && depth == 3 && "name".equals(local) && !recording.nameSeen) {
                recording.nameSeen = true;
                capture("name");
            } else if (section != null && depth == section.depth + 1
                       && (section.recording.route ? "rtept" : "trkpt").equals(local)) {
                if (points.size() >= MAX_POINTS)
                    throw new ReviewLimitExceeded("Source exceeds the review limit of " + MAX_POINTS + " observations.");
                point = new Point(attributes);
                reserve(OBSERVATION_UNITS);
            } else if (point != null && depth == section.depth + 2) {
                if ("time".equals(local) && !point.timeSeen) {
                    point.timeSeen = true;
                    capture("time");
                }
                if ("ele".equals(local) && !point.elevationSeen) {
                    point.elevationSeen = true;
                    capture("ele");
                }
            }
        }

        private void startSection() {
            section = new Section(recording, points.size(), depth);
            sections.add(section);
            reserve(SECTION_UNITS);
        }

        @Override
        public void characters(char[] characters, int start, int length) {
            if (text == null) return;
            if (text.length() + length > MAX_FIELD_CHARACTERS)
                throw new ReviewLimitExceeded("Source exceeds the review text limit for a recording name, time or elevation.");
            text.append(characters, start, length);
            if (!"ele".equals(field)) reserve((long) Character.BYTES * length);
        }

        @Override
        public void endElement(String uri, String local, String qualified) {
            if (text != null && depth == fieldDepth) {
                String value = text.toString();
                switch (field) {
                    case "name" -> recording.name = value;
                    case "time" -> point.time = value;
                    case "ele" -> point.elevation = value;
                    default -> throw new IllegalStateException("Unknown GPX review field.");
                }
                text = null;
                field = null;
            }
            if (uri.equals(namespace)) {
                if (point != null && depth == section.depth + 1) finishPoint();
                if (section != null && depth == section.depth) {
                    section.count = points.size() - section.first;
                    section = null;
                }
                if (recording != null && depth == 2) recording = null;
            }
            depth--;
        }

        private void finishPoint() {
            int index = points.size();
            int previousProblems = problems.size();
            Instant timestamp = null;
            if (point.time != null && !point.time.isBlank()) {
                try {
                    var parsed = GpxTimestampParser.parse(point.time);
                    timestamp = parsed.timestamp();
                    if (parsed.assumedUtc()) assumedUtcTimes++;
                } catch (DateTimeParseException e) {
                    problems.add(new ParseProblem(index, "time", "Timestamp is not a valid GPX date and time."));
                    if (!warnedTime) LOG.log(System.Logger.Level.WARNING,
                            "GPX timestamp parsing: point={0}, type={1}, message=Invalid GPX timestamp; remaining occurrences are counted in the report",
                            index, e.getClass().getSimpleName());
                    warnedTime = true;
                }
            }
            Double latitude = number(point.latitude, index, "latitude", problems);
            Double longitude = number(point.longitude, index, "longitude", problems);
            Double elevation = number(point.elevation, index, "elevation", problems);
            reserve((long) PROBLEM_UNITS * (problems.size() - previousProblems)
                    + (timestamp == null ? 0 : INSTANT_UNITS)
                    + (long) NUMBER_UNITS * ((latitude == null ? 0 : 1) + (longitude == null ? 0 : 1) + (elevation == null ? 0 : 1)));
            points.add(new TrackObservation(index, sections.size() - 1, latitude, longitude, elevation, timestamp, point.time, point.estimated));
            point = null;
        }

        @Override
        public void warning(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    }

    public static GpxDocument parse(String source) {
        return parse(source.getBytes(StandardCharsets.UTF_8));
    }

    public String sourceHash() {
        return sourceHash;
    }

    public List<TrackObservation> points() {
        return points;
    }

    public List<TrackSection> sections() {
        return sections;
    }

    public List<ParseProblem> problems() {
        return problems;
    }

    /**
     * Original XML for the importer, decoded using the encoding detected by the XML parser.
     */
    public String sourceXml() {
        String xml = new String(source, encoding);
        return xml.startsWith("\uFEFF") ? xml.substring(1) : xml;
    }

    public static String hash(byte[] bytes) {
        return HexFormat.of().formatHex(sha256().digest(bytes));
    }

    /**
     * Hash a caller-owned stream without retaining its contents or closing it.
     */
    public static String hash(InputStream input) throws IOException {
        MessageDigest digest = sha256();
        new DigestInputStream(input, digest).transferTo(OutputStream.nullOutputStream());
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            LOG.log(System.Logger.Level.ERROR, "Cannot hash GPX source: SHA-256 unavailable", e);
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private static Double number(String value, int index, String field, List<ParseProblem> problems) {
        if (value == null || value.isBlank()) return null;
        if (NUMBER.matcher(value.trim()).matches()) {
            double number = Double.parseDouble(value.trim());
            if (Double.isFinite(number)) return number;
        }
        problems.add(new ParseProblem(index, field, "Value is not a finite number."));
        return null;
    }
}

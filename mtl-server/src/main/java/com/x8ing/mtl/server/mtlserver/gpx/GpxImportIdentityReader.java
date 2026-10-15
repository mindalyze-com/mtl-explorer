package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.gpsquality.diagnostics.GpxTimestampParser;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Preserves observation identities and normalizes clocks before the lenient GPX parser.
 */
final class GpxImportIdentityReader extends Reader {
    static final String NAMESPACE = "https://mytraillog.com/xmlschemas/import-identity/v1";
    private static final int MAX_TIMESTAMP_FIELD_CHARACTERS = 64 * 1024;
    private static final System.Logger LOG = System.getLogger(GpxImportIdentityReader.class.getName());
    private final Reader source;
    private final XMLEventReader input;
    private final XMLEventWriter output;
    private final XMLEventFactory events = XMLEventFactory.newFactory();
    private final StringWriter buffer = new StringWriter();
    private final Deque<String> ancestors = new ArrayDeque<>();
    private int offset, depth, sectionDepth = -1, pointDepth = -1, pointIndex, sectionIndex = -1;
    private boolean hasExtensions;
    private QName pointName;
    private String sectionName;
    private String gpxNamespace;
    private XMLEvent timestampStart;
    private final StringBuilder timestampText = new StringBuilder();
    private int timestampDepth, assumedUtcTimes, invalidTimes;
    private boolean timestampTooLong, duplicateTime, hasTime;

    GpxImportIdentityReader(Reader source) throws XMLStreamException {
        this.source = source;
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        input = factory.createXMLEventReader(source);
        output = XMLOutputFactory.newFactory().createXMLEventWriter(buffer);
    }

    @Override
    public int read(char[] target, int start, int length) throws IOException {
        if (length == 0) return 0;
        try {
            while (offset == buffer.getBuffer().length()) {
                buffer.getBuffer().setLength(0);
                offset = 0;
                if (!input.hasNext()) return -1;
                copy(input.nextEvent());
                output.flush();
            }
            int count = Math.min(length, buffer.getBuffer().length() - offset);
            buffer.getBuffer().getChars(offset, offset + count, target, start);
            offset += count;
            return count;
        } catch (XMLStreamException error) {
            throw new IOException("Cannot preserve GPX source observation identities", error);
        }
    }

    private void copy(XMLEvent event) throws XMLStreamException {
        if (timestampStart != null) {
            if (event.isStartElement()) timestampDepth++;
            else if (event.isEndElement() && --timestampDepth == 0) finishTimestamp(event);
            else if (event.isCharacters() && !timestampTooLong && !duplicateTime) {
                String text = event.asCharacters().getData();
                if (timestampText.length() + text.length() > MAX_TIMESTAMP_FIELD_CHARACTERS) timestampTooLong = true;
                else timestampText.append(text);
            }
            return;
        }
        if (event.isStartElement() && isTimestamp(event.asStartElement().getName())) {
            timestampStart = event;
            timestampDepth = 1;
            timestampText.setLength(0);
            timestampTooLong = false;
            duplicateTime = pointDepth == depth && hasTime;
            if (pointDepth == depth) hasTime = true;
            return;
        }
        if (event.isStartElement()) {
            depth++;
            QName name = event.asStartElement().getName();
            String local = name.getLocalPart();
            if (depth == 1 && "gpx".equals(local)) gpxNamespace = name.getNamespaceURI();
            if (("rte".equals(local) && depth == 2 && "gpx".equals(ancestors.peek()))
                || ("trkseg".equals(local) && depth == 3 && "trk".equals(ancestors.peek()))) {
                sectionIndex++;
                sectionDepth = depth;
                sectionName = local;
            }
            if (depth == sectionDepth + 1 && ("rte".equals(sectionName) ? "rtept" : "trkpt").equals(local)) {
                pointDepth = depth;
                pointName = name;
                hasExtensions = false;
                hasTime = false;
            } else if (depth == pointDepth + 1 && "extensions".equals(local)) hasExtensions = true;
            ancestors.push(local);
        } else if (event.isEndElement()) {
            QName name = event.asEndElement().getName();
            if (pointDepth >= 0 && depth == pointDepth + 1 && "extensions".equals(name.getLocalPart())) identity();
            if (pointDepth == depth) {
                if (!hasExtensions) {
                    QName extension = new QName(pointName.getNamespaceURI(), "extensions", pointName.getPrefix());
                    output.add(events.createStartElement(extension, null, null));
                    identity();
                    output.add(events.createEndElement(extension, null));
                }
                pointIndex++;
                pointDepth = -1;
            }
            if (sectionDepth == depth) sectionDepth = -1;
            ancestors.pop();
            depth--;
        }
        output.add(event);
    }

    private boolean isTimestamp(QName name) {
        if (!"time".equals(name.getLocalPart()) || !name.getNamespaceURI().equals(gpxNamespace)) return false;
        return pointDepth >= 0 && depth == pointDepth && pointName.getNamespaceURI().equals(gpxNamespace)
               || depth == 2 && ("metadata".equals(ancestors.peek()) || "wpt".equals(ancestors.peek()))
               || depth == 1 && "gpx".equals(ancestors.peek());
    }

    private void finishTimestamp(XMLEvent end) throws XMLStreamException {
        try {
            if (duplicateTime) return;
            if (timestampTooLong) throw new DateTimeParseException("GPX timestamp exceeds the field limit.", "", 0);
            var parsed = GpxTimestampParser.parse(timestampText.toString());
            if (parsed.timestamp() != null) {
                output.add(timestampStart);
                output.add(events.createCharacters(parsed.timestamp().toString()));
                output.add(end);
                if (parsed.assumedUtc()) assumedUtcTimes++;
            }
        } catch (DateTimeParseException error) {
            invalidTimes++;
            LOG.log(invalidTimes == 1 ? System.Logger.Level.WARNING : System.Logger.Level.DEBUG,
                    "GPX import timestamp rejected: point={0}, type={1}, message=Invalid GPX timestamp; geometry is retained without this time",
                    pointIndex, error.getClass().getSimpleName());
        } finally {
            timestampStart = null;
            timestampText.setLength(0);
        }
    }

    private void identity() throws XMLStreamException {
        QName name = new QName(NAMESPACE, "observation", "mtli");
        output.add(events.createStartElement(name, List.of(
                        events.createAttribute("point", Integer.toString(pointIndex)),
                        events.createAttribute("section", Integer.toString(sectionIndex))).iterator(),
                List.of(events.createNamespace("mtli", NAMESPACE)).iterator()));
        output.add(events.createEndElement(name, null));
    }

    @Override
    public void close() throws IOException {
        try {
            input.close();
            output.close();
        } catch (XMLStreamException error) {
            throw new IOException("Cannot close GPX identity reader", error);
        } finally {
            if (assumedUtcTimes > 0 || invalidTimes > 0) LOG.log(System.Logger.Level.INFO,
                    "GPX import timestamp normalization summary: assumedUtc={0}, invalid={1}", assumedUtcTimes, invalidTimes);
            source.close();
        }
    }
}

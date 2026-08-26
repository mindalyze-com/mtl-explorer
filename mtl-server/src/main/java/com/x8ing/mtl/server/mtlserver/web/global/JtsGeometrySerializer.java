package com.x8ing.mtl.server.mtlserver.web.global;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Serializes JTS geometries as GeoJSON without depending on the Jackson 2-only JTS module.
 */
public final class JtsGeometrySerializer extends ValueSerializer<Geometry> {

    private static final String COORDINATES = "coordinates";
    private static final String GEOMETRIES = "geometries";
    private static final String TYPE = "type";

    @Override
    public void serialize(Geometry geometry, JsonGenerator generator, SerializationContext context)
            throws JacksonException {
        writeGeometry(generator, geometry);
    }

    @Override
    public Class<Geometry> handledType() {
        return Geometry.class;
    }

    private void writeGeometry(JsonGenerator generator, Geometry geometry) throws JacksonException {
        if (geometry instanceof Polygon polygon) {
            writePolygon(generator, polygon);
        } else if (geometry instanceof Point point) {
            writePoint(generator, point);
        } else if (geometry instanceof MultiPoint multiPoint) {
            writeMultiPoint(generator, multiPoint);
        } else if (geometry instanceof MultiPolygon multiPolygon) {
            writeMultiPolygon(generator, multiPolygon);
        } else if (geometry instanceof LineString lineString) {
            writeLineString(generator, lineString);
        } else if (geometry instanceof MultiLineString multiLineString) {
            writeMultiLineString(generator, multiLineString);
        } else if (geometry instanceof GeometryCollection geometryCollection) {
            writeGeometryCollection(generator, geometryCollection);
        } else {
            throw new IllegalArgumentException("Unsupported JTS geometry type: " + geometry.getClass().getName());
        }
    }

    private void writeGeometryCollection(JsonGenerator generator, GeometryCollection collection)
            throws JacksonException {
        generator.writeStartObject();
        generator.writeStringProperty(TYPE, "GeometryCollection");
        generator.writeArrayPropertyStart(GEOMETRIES);
        for (int i = 0; i < collection.getNumGeometries(); i++) {
            writeGeometry(generator, collection.getGeometryN(i));
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private void writeMultiPoint(JsonGenerator generator, MultiPoint multiPoint) throws JacksonException {
        generator.writeStartObject();
        generator.writeStringProperty(TYPE, "MultiPoint");
        generator.writeArrayPropertyStart(COORDINATES);
        for (int i = 0; i < multiPoint.getNumGeometries(); i++) {
            writeCoordinate(generator, ((Point) multiPoint.getGeometryN(i)).getCoordinate());
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private void writeMultiLineString(JsonGenerator generator, MultiLineString multiLineString)
            throws JacksonException {
        generator.writeStartObject();
        generator.writeStringProperty(TYPE, "MultiLineString");
        generator.writeArrayPropertyStart(COORDINATES);
        for (int i = 0; i < multiLineString.getNumGeometries(); i++) {
            writeLineStringCoordinates(generator, (LineString) multiLineString.getGeometryN(i));
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private void writeMultiPolygon(JsonGenerator generator, MultiPolygon multiPolygon) throws JacksonException {
        generator.writeStartObject();
        generator.writeStringProperty(TYPE, "MultiPolygon");
        generator.writeArrayPropertyStart(COORDINATES);
        for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
            writePolygonCoordinates(generator, (Polygon) multiPolygon.getGeometryN(i));
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private void writePolygon(JsonGenerator generator, Polygon polygon) throws JacksonException {
        generator.writeStartObject();
        generator.writeStringProperty(TYPE, "Polygon");
        generator.writeName(COORDINATES);
        writePolygonCoordinates(generator, polygon);
        generator.writeEndObject();
    }

    private void writePolygonCoordinates(JsonGenerator generator, Polygon polygon) throws JacksonException {
        generator.writeStartArray();
        writeLineStringCoordinates(generator, polygon.getExteriorRing());
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            writeLineStringCoordinates(generator, polygon.getInteriorRingN(i));
        }
        generator.writeEndArray();
    }

    private void writeLineString(JsonGenerator generator, LineString lineString) throws JacksonException {
        generator.writeStartObject();
        generator.writeStringProperty(TYPE, "LineString");
        generator.writeName(COORDINATES);
        writeLineStringCoordinates(generator, lineString);
        generator.writeEndObject();
    }

    private void writeLineStringCoordinates(JsonGenerator generator, LineString lineString) throws JacksonException {
        generator.writeStartArray();
        for (Coordinate coordinate : lineString.getCoordinates()) {
            writeCoordinate(generator, coordinate);
        }
        generator.writeEndArray();
    }

    private void writePoint(JsonGenerator generator, Point point) throws JacksonException {
        generator.writeStartObject();
        generator.writeStringProperty(TYPE, "Point");
        generator.writeName(COORDINATES);
        writeCoordinate(generator, point.getCoordinate());
        generator.writeEndObject();
    }

    private void writeCoordinate(JsonGenerator generator, Coordinate coordinate) throws JacksonException {
        generator.writeStartArray();
        if (coordinate != null) {
            generator.writeNumber(coordinate.getX());
            generator.writeNumber(coordinate.getY());
            if (!Double.isNaN(coordinate.getZ())) {
                generator.writeNumber(coordinate.getZ());
            }
        }
        generator.writeEndArray();
    }
}

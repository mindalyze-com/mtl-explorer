/** Source diagnosis without application, persistence, or repair dependencies. */
module com.x8ing.mtl.gpsquality.diagnostics {
    requires transitive java.xml;

    exports com.x8ing.mtl.server.gpsquality.diagnostics;
}

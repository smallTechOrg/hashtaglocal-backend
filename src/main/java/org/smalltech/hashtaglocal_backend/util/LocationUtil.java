package org.smalltech.hashtaglocal_backend.util;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public class LocationUtil {
  private static final GeometryFactory geometryFactory =
      new GeometryFactory(new PrecisionModel(), 4326);

  public static Point createPoint(Double latitude, Double longitude) {
    if (latitude == null || longitude == null) {
      throw new IllegalArgumentException("Latitude and longitude cannot be null");
    }
    return geometryFactory.createPoint(new Coordinate(longitude, latitude));
  }

  public static Double getLatitude(Point point) {
    if (point == null) {
      return null;
    }
    return point.getY();
  }

  public static Double getLongitude(Point point) {
    if (point == null) {
      return null;
    }
    return point.getX();
  }
}

package org.smalltech.hashtaglocal_backend.service;

import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.util.GeoUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GeoFenceService {

  public void assertWithinRadius(
      Location issueLocation, double userLat, double userLng, double radiusMeters) {
    if (issueLocation == null || issueLocation.getPoint() == null) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Issue location not available");
    }

    double issueLat = issueLocation.getPoint().getY(); // latitude
    double issueLng = issueLocation.getPoint().getX(); // longitude

    double distance = GeoUtil.distanceMeters(issueLat, issueLng, userLat, userLng);

    if (distance > radiusMeters) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "You must be within "
              + Math.round(radiusMeters)
              + " meters of the issue to perform this action");
    }
  }
}

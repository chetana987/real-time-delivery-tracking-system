package com.deliverytracking.service;

import com.deliverytracking.dto.NearbyPartner;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Live delivery-partner locations backed by Redis geospatial indexes.
 * <p>
 * Redis stores each partner id as a member of a sorted set keyed by a 52-bit
 * geohash of their (lng, lat). GEOADD inserts or updates a member, GEOSEARCH
 * (GEORADIUS) walks the sorted set from the queried point and returns members
 * within a radius in distance order. Reads are O(log N + M), so even with tens
 * of thousands of online partners a nearest-neighbour lookup is sub-millisecond.
 * <p>
 * All operations are best-effort: a Redis outage degrades dispatch to
 * "no nearby partners" instead of failing order placement.
 */
@Service
@RequiredArgsConstructor
public class PartnerGeoService {

    private static final Logger log = LoggerFactory.getLogger(PartnerGeoService.class);
    private static final String GEO_KEY = "delivery:partners:geo";

    private final StringRedisTemplate stringRedisTemplate;

    public void addPartnerLocation(Long partnerId, double lat, double lng) {
        try {
            geo().add(GEO_KEY, new Point(lng, lat), String.valueOf(partnerId));
        } catch (RuntimeException e) {
            log.warn("Could not GEOADD partner {} at ({}, {})", partnerId, lat, lng, e);
        }
    }

    public void updatePartnerLocation(Long partnerId, double lat, double lng) {
        addPartnerLocation(partnerId, lat, lng);
    }

    public void removePartnerLocation(Long partnerId) {
        try {
            geo().remove(GEO_KEY, String.valueOf(partnerId));
        } catch (RuntimeException e) {
            log.warn("Could not remove partner {} from geo index", partnerId, e);
        }
    }

    /**
     * Delivery partners online and within {@code radiusKm} of (lat, lng),
     * closest first. Returns an empty list if Redis is unavailable.
     */
    public List<NearbyPartner> findNearestPartners(double lat, double lng, double radiusKm, int limit) {
        if (radiusKm <= 0) {
            return List.of();
        }

        try {
            Circle circle = new Circle(new Point(lng, lat),
                    new Distance(radiusKm, RedisGeoCommands.DistanceUnit.KILOMETERS));
            RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                    .newGeoRadiusArgs()
                    .limit(limit)
                    .sortAscending();

            GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                    geo().radius(GEO_KEY, circle, args);
            if (results == null) {
                return List.of();
            }

            return results.getContent().stream()
                    .map(result -> NearbyPartner.builder()
                            .deliveryPartnerId(Long.valueOf(result.getContent().getName()))
                            .distanceKm(result.getDistance() != null ? result.getDistance().getValue() : null)
                            .build())
                    .toList();
        } catch (RuntimeException e) {
            log.warn("Could not search geo index near ({}, {})", lat, lng, e);
            return List.of();
        }
    }

    private GeoOperations<String, String> geo() {
        return stringRedisTemplate.opsForGeo();
    }
}

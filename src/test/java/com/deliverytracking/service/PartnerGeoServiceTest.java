package com.deliverytracking.service;

import com.deliverytracking.dto.NearbyPartner;
import org.junit.jupiter.api.Test;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PartnerGeoServiceTest {

    @Test
    void findNearestPartners_mapsPartnerIdAndDistanceFromGeoResults() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        GeoOperations<String, String> geoOperations = mock(GeoOperations.class);
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);

        GeoResult<RedisGeoCommands.GeoLocation<String>> first = new GeoResult<>(
                new RedisGeoCommands.GeoLocation<>("11", new Point(77.6, 12.9)),
                new Distance(1.25, RedisGeoCommands.DistanceUnit.KILOMETERS)
        );
        GeoResult<RedisGeoCommands.GeoLocation<String>> second = new GeoResult<>(
                new RedisGeoCommands.GeoLocation<>("22", new Point(77.61, 12.91)),
                new Distance(2.5, RedisGeoCommands.DistanceUnit.KILOMETERS)
        );
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = new GeoResults<>(List.of(first, second));

        when(geoOperations.radius(eq("delivery:partners:geo"), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .thenReturn(geoResults);

        PartnerGeoService service = new PartnerGeoService(redisTemplate);

        List<NearbyPartner> partners = service.findNearestPartners(12.9, 77.6, 5, 10);

        assertThat(partners).hasSize(2);
        assertThat(partners).extracting(NearbyPartner::getDeliveryPartnerId).containsExactly(11L, 22L);
        assertThat(partners).extracting(NearbyPartner::getDistanceKm).containsExactly(1.25, 2.5);
    }
}

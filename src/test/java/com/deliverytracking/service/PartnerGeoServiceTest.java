package com.deliverytracking.service;

import com.deliverytracking.dto.NearbyPartner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartnerGeoServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private GeoOperations<String, String> geoOperations;

    @Test
    void findNearestPartners_mapsPartnerIdAndDistanceFromGeoResults() {
        when(stringRedisTemplate.opsForGeo()).thenReturn(geoOperations);
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = new GeoResults<>(List.of(
                new GeoResult<>(
                        new RedisGeoCommands.GeoLocation<>("99", new Point(77.0, 12.0)),
                        new Distance(2.4, RedisGeoCommands.DistanceUnit.KILOMETERS)
                )
        ));
        when(geoOperations.radius(eq("delivery:partners:geo"), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .thenReturn(geoResults);

        PartnerGeoService service = new PartnerGeoService(stringRedisTemplate);
        List<NearbyPartner> nearbyPartners = service.findNearestPartners(12.0, 77.0, 5.0, 5);

        assertThat(nearbyPartners).hasSize(1);
        assertThat(nearbyPartners.get(0).getDeliveryPartnerId()).isEqualTo(99L);
        assertThat(nearbyPartners.get(0).getDistanceKm()).isEqualTo(2.4);
    }
}

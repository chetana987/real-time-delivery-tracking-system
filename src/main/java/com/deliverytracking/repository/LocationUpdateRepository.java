package com.deliverytracking.repository;

import com.deliverytracking.entity.LocationUpdate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocationUpdateRepository extends JpaRepository<LocationUpdate, Long> {

    List<LocationUpdate> findByOrderIdOrderByTimestampDesc(Long orderId);

    Page<LocationUpdate> findByOrderId(Long orderId, Pageable pageable);

    Optional<LocationUpdate> findTopByOrderIdOrderByTimestampDesc(Long orderId);

    Optional<LocationUpdate> findFirstByDeliveryPartnerIdOrderByTimestampDesc(Long deliveryPartnerId);
}

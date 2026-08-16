package com.saferide.ride;

import com.saferide.vehicle.VehicleType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RideRepository extends JpaRepository<Ride, Long> {

    Page<Ride> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    /** The one ride (if any) a driver is currently busy with - used to route a location update to the right WebSocket topic. */
    Optional<Ride> findFirstByDriverIdAndStatusIn(Long driverId, Collection<RideStatus> statuses);

    Page<Ride> findByDriverIdOrderByCreatedAtDesc(Long driverId, Pageable pageable);

    /** Fallback for a ride matching couldn't auto-assign - a driver can still self-claim it manually. */
    Page<Ride> findByStatusAndVehicleTypeOrderByCreatedAtAsc(RideStatus status, VehicleType vehicleType, Pageable pageable);

    boolean existsByDriverIdAndStatusIn(Long driverId, Collection<RideStatus> statuses);

    /** Same "is this driver already busy" check, excluding one ride - lets a driver accept the very ride they were just proactively assigned to. */
    boolean existsByDriverIdAndStatusInAndIdNot(Long driverId, Collection<RideStatus> statuses, Long excludedRideId);

    boolean existsByCustomerIdAndStatusIn(Long customerId, Collection<RideStatus> statuses);

    /**
     * Session-free ownership checks for {@code StompAuthChannelInterceptor} - a plain
     * {@code findById} followed by {@code ride.getCustomer()}/{@code ride.getDriver().getUser()}
     * would try to initialize a lazy proxy outside any transaction (the interceptor runs on the
     * WebSocket message channel, not a request thread) and throw
     * {@code LazyInitializationException}. An {@code EXISTS} query needs no entity hydration at
     * all, so there's no session to be missing.
     */
    boolean existsByIdAndCustomerId(Long rideId, Long customerId);

    boolean existsByIdAndDriver_User_Id(Long rideId, Long driverUserId);

    /** Admin analytics summary (Phase 13) - one query covers completed/cancelled/(by subtraction) active counts. */
    long countByStatusIn(Collection<RideStatus> statuses);
}

package com.saferide.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByRideId(Long rideId);

    /** Admin analytics summary (Phase 13) - coalesced to 0 rather than null when nothing's ever been charged yet. */
    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = :status")
    double sumAmountByStatus(@Param("status") PaymentStatus status);

    /** One driver's real take-home total for the admin driver detail screen - actual charged payments, not the locked fare on every completed ride (a ride can complete without ever being paid). */
    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = :status and p.ride.driver.id = :driverId")
    double sumAmountByStatusAndDriverId(@Param("status") PaymentStatus status, @Param("driverId") Long driverId);
}

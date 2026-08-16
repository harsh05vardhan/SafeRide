package com.saferide.complaint;

import com.saferide.auth.AuthenticatedUser;
import com.saferide.complaint.dto.ComplaintRequest;
import com.saferide.complaint.dto.ComplaintResponse;
import com.saferide.driver.Driver;
import com.saferide.driver.DriverReliabilityService;
import com.saferide.exception.ResourceNotFoundException;
import com.saferide.exception.UnauthorizedException;
import com.saferide.ride.Ride;
import com.saferide.ride.RideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Filing and viewing complaints - the disputable-event half of "the
 * driver cannot demand an unauthorized extra fare" (see the README's "Why
 * SafeRide" table). Deliberately independent of {@code RideStateMachine}:
 * a complaint doesn't move the ride through any status, it's a standalone
 * record attached to one, filable by either side at any point once the
 * ride exists.
 */
@Service
@RequiredArgsConstructor
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final RideRepository rideRepository;
    private final DriverReliabilityService reliabilityService;

    @Transactional
    public ComplaintResponse file(AuthenticatedUser principal, Long rideId, ComplaintRequest request) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> ResourceNotFoundException.of("Ride", rideId));

        boolean isCustomer = ride.getCustomer().getId().equals(principal.userId());
        boolean isAssignedDriver = ride.getDriver() != null
                && ride.getDriver().getUser().getId().equals(principal.userId());
        if (!isCustomer && !isAssignedDriver) {
            throw new UnauthorizedException("This ride does not belong to you");
        }

        Complaint complaint = new Complaint();
        complaint.setRide(ride);
        complaint.setType(request.type());
        complaint.setDescription(request.description());
        complaint.setStatus(ComplaintStatus.OPEN);

        if (isCustomer) {
            complaint.setFiledBy(ride.getCustomer());
            complaint.setFiledByRole(ComplaintFiledBy.CUSTOMER);
            // Counts toward the driver's complaintCount/reliability regardless of complaint type -
            // see ReliabilityCalculator's generic complaint penalty. Filing about a customer has no
            // symmetric stat to update - Customer/User has no complaintCount field.
            Driver driver = ride.getDriver();
            if (driver != null) {
                reliabilityService.recordComplaint(driver);
            }
        } else {
            complaint.setFiledBy(ride.getDriver().getUser());
            complaint.setFiledByRole(ComplaintFiledBy.DRIVER);
        }

        return ComplaintResponse.from(complaintRepository.save(complaint));
    }

    @Transactional(readOnly = true)
    public Page<ComplaintResponse> listMine(Long userId, Pageable pageable) {
        return complaintRepository.findByFiledByIdOrderByCreatedAtDesc(userId, pageable).map(ComplaintResponse::from);
    }
}

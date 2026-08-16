package com.saferide.notification;

import com.saferide.exception.ResourceNotFoundException;
import com.saferide.exception.UnauthorizedException;
import com.saferide.notification.dto.NotificationResponse;
import com.saferide.user.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns {@link Notification} persistence. {@link #create} is called only by
 * {@link NotificationEventListener} - never from a controller, so an
 * in-app notification always traces back to a real domain event, not an
 * arbitrary write.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * {@code REQUIRES_NEW} is not optional here. The sole caller,
     * {@link NotificationEventListener}, runs at {@code AFTER_COMMIT} -
     * per Spring's own documented caveat for that phase, the triggering
     * transaction's resources are still bound to the thread at that point
     * (final cleanup happens one step later), so a plain {@code REQUIRED}
     * write here would silently "join" a transaction that has already
     * committed and will never commit again - the insert is attempted but
     * nothing is ever flushed. A genuinely new transaction is required to
     * actually persist the notification.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(Long userId, NotificationType type, String title, String message, Long rideId) {
        Notification notification = new Notification();
        notification.setUser(userRepository.getReferenceById(userId));
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRideId(rideId);
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listMine(Long userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return page.map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", notificationId));
        if (!notification.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("This notification does not belong to you");
        }
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId, Instant.now());
    }
}

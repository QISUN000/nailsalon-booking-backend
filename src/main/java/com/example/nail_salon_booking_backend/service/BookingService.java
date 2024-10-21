package com.example.nail_salon_booking_backend.service;

import com.example.nail_salon_booking_backend.model.Booking;
import com.example.nail_salon_booking_backend.model.Professional;
import com.example.nail_salon_booking_backend.model.User;
import com.example.nail_salon_booking_backend.repository.BookingRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ProfessionalService professionalService;
    private final NotificationService notificationService;

    public BookingService(BookingRepository bookingRepository, ProfessionalService professionalService, NotificationService notificationService) {
        this.bookingRepository = bookingRepository;
        this.professionalService = professionalService;
        this.notificationService = notificationService;
    }

    @Transactional
    public Booking createBooking(Booking booking, User currentUser) {
        if (!booking.getCustomer().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You can only create bookings for yourself");
        }

        if (!isProfessionalAvailable(booking.getProfessional(), booking.getStartTime(), booking.getEndTime())) {
            throw new IllegalStateException("Professional is not available for the selected time slot");
        }

        booking.setStatus(Booking.BookingStatus.SCHEDULED);
        Booking savedBooking = bookingRepository.save(booking);

        notificationService.sendBookingConfirmation(savedBooking);
        return savedBooking;
    }

    public Optional<Booking> getBookingById(Long id, User currentUser) {
        Optional<Booking> booking = bookingRepository.findById(id);
        if (booking.isPresent() && !isAuthorizedToView(booking.get(), currentUser)) {
            throw new AccessDeniedException("You are not authorized to view this booking");
        }
        return booking;
    }

    public List<Booking> getBookingsForCustomer(User customer, User currentUser) {
        if (!customer.getId().equals(currentUser.getId()) && !currentUser.getRole().equals(User.UserRole.ADMIN)) {
            throw new AccessDeniedException("You can only view your own bookings");
        }
        return bookingRepository.findByCustomer(customer);
    }

    public List<Booking> getBookingsForProfessional(Professional professional, User currentUser) {
        if (!professional.getUser().getId().equals(currentUser.getId()) && !currentUser.getRole().equals(User.UserRole.ADMIN)) {
            throw new AccessDeniedException("You can only view bookings for your own professional profile");
        }
        return bookingRepository.findByProfessional(professional);
    }

    @Transactional
    public Booking updateBooking(Long id, Booking updatedBooking, User currentUser) {
        return bookingRepository.findById(id)
                .map(booking -> {
                    if (!isAuthorizedToModify(booking, currentUser)) {
                        throw new AccessDeniedException("You are not authorized to modify this booking");
                    }
                    booking.setStartTime(updatedBooking.getStartTime());
                    booking.setEndTime(updatedBooking.getEndTime());
                    booking.setService(updatedBooking.getService());
                    booking.setProfessional(updatedBooking.getProfessional());
                    Booking savedBooking = bookingRepository.save(booking);
                    notificationService.sendBookingUpdateNotification(savedBooking);
                    return savedBooking;
                })
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
    }

    @Transactional
    public void cancelBooking(Long id, User currentUser) {
        bookingRepository.findById(id)
                .ifPresent(booking -> {
                    if (!isAuthorizedToModify(booking, currentUser)) {
                        throw new AccessDeniedException("You are not authorized to cancel this booking");
                    }
                    booking.setStatus(Booking.BookingStatus.CANCELLED);
                    bookingRepository.save(booking);
                    notificationService.sendBookingCancellationNotification(booking);
                });
    }

    private boolean isProfessionalAvailable(Professional professional, LocalDateTime start, LocalDateTime end) {
        List<Booking> existingBookings = bookingRepository.findByProfessionalAndStartTimeBetween(
                professional, start, end);
        return existingBookings.isEmpty();
    }

    private boolean isAuthorizedToView(Booking booking, User user) {
        return booking.getCustomer().getId().equals(user.getId()) ||
                booking.getProfessional().getUser().getId().equals(user.getId()) ||
                user.getRole().equals(User.UserRole.ADMIN);
    }

    private boolean isAuthorizedToModify(Booking booking, User user) {
        return booking.getCustomer().getId().equals(user.getId()) ||
                user.getRole().equals(User.UserRole.ADMIN);
    }
}
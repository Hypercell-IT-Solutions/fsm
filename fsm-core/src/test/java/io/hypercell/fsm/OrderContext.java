package io.hypercell.fsm;

/**
 * Shared test fixture for an order workflow context.
 */
public class OrderContext {
    private final String orderId;
    private String reservationId;
    private boolean paymentCharged;
    private boolean confirmationSent;

    public OrderContext(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String id) {
        this.reservationId = id;
    }

    public boolean isPaymentCharged() {
        return paymentCharged;
    }

    public void setPaymentCharged(boolean v) {
        this.paymentCharged = v;
    }

    public boolean isConfirmationSent() {
        return confirmationSent;
    }

    public void setConfirmationSent(boolean v) {
        this.confirmationSent = v;
    }
}

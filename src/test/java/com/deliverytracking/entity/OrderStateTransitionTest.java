package com.deliverytracking.entity;

import com.deliverytracking.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateTransitionTest {

    private Order orderIn(OrderStatus status) {
        return Order.builder().status(status).build();
    }

    @Test
    void placed_toAccepted_isAllowed() {
        Order order = orderIn(OrderStatus.PLACED);
        order.transitionTo(OrderStatus.ACCEPTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    void accepted_toPickedUp_isAllowed() {
        Order order = orderIn(OrderStatus.ACCEPTED);
        order.transitionTo(OrderStatus.PICKED_UP);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PICKED_UP);
    }

    @Test
    void pickedUp_toOutForDelivery_isAllowed() {
        Order order = orderIn(OrderStatus.PICKED_UP);
        order.transitionTo(OrderStatus.OUT_FOR_DELIVERY);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
    }

    @Test
    void outForDelivery_toDelivered_isAllowed() {
        Order order = orderIn(OrderStatus.OUT_FOR_DELIVERY);
        order.transitionTo(OrderStatus.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void fullDeliveryPipeline_completesEveryStepInOrder() {
        Order order = orderIn(OrderStatus.PLACED);
        order.transitionTo(OrderStatus.ACCEPTED);
        order.transitionTo(OrderStatus.PICKED_UP);
        order.transitionTo(OrderStatus.OUT_FOR_DELIVERY);
        order.transitionTo(OrderStatus.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void skippingStates_isRejected() {
        assertThatThrownBy(() -> orderIn(OrderStatus.PLACED).transitionTo(OrderStatus.PICKED_UP))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> orderIn(OrderStatus.PLACED).transitionTo(OrderStatus.DELIVERED))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> orderIn(OrderStatus.ACCEPTED).transitionTo(OrderStatus.OUT_FOR_DELIVERY))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> orderIn(OrderStatus.PICKED_UP).transitionTo(OrderStatus.DELIVERED))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void sameStateTransition_isRejected() {
        assertThatThrownBy(() -> orderIn(OrderStatus.PLACED).transitionTo(OrderStatus.PLACED))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void delivered_isFinalState() {
        Order order = orderIn(OrderStatus.DELIVERED);
        assertThatThrownBy(() -> order.transitionTo(OrderStatus.ACCEPTED))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> order.transitionTo(OrderStatus.OUT_FOR_DELIVERY))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void placed_toCancelled_isAllowed() {
        Order order = orderIn(OrderStatus.PLACED);
        order.transitionTo(OrderStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelled_isFinalState() {
        Order order = orderIn(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> order.transitionTo(OrderStatus.PLACED))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> order.transitionTo(OrderStatus.ACCEPTED))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> order.transitionTo(OrderStatus.DELIVERED))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void ordersPastPlaced_cannotBeCancelled() {
        assertThatThrownBy(() -> orderIn(OrderStatus.ACCEPTED).transitionTo(OrderStatus.CANCELLED))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> orderIn(OrderStatus.PICKED_UP).transitionTo(OrderStatus.CANCELLED))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> orderIn(OrderStatus.OUT_FOR_DELIVERY).transitionTo(OrderStatus.CANCELLED))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> orderIn(OrderStatus.DELIVERED).transitionTo(OrderStatus.CANCELLED))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void nullTargetStatus_isRejected() {
        assertThatThrownBy(() -> orderIn(OrderStatus.PLACED).transitionTo(null))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void failedTransition_leavesCurrentStatusUnchanged() {
        Order order = orderIn(OrderStatus.PLACED);
        try {
            order.transitionTo(OrderStatus.DELIVERED);
        } catch (InvalidStateTransitionException ignored) {
            // expected
        }
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
    }
}

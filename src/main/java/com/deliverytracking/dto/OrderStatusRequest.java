package com.deliverytracking.dto;

import com.deliverytracking.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Payload to update an order's status (delivery partner only)")
public class OrderStatusRequest {

    @NotNull
    @Schema(description = "Target status. Only the next valid state is allowed (see order state machine).",
            example = "PICKED_UP")
    private OrderStatus status;
}

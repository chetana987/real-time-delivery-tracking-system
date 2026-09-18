package com.deliverytracking.controller;

import com.deliverytracking.dto.ApiError;
import com.deliverytracking.dto.OrderFilter;
import com.deliverytracking.dto.OrderResponse;
import com.deliverytracking.dto.OrderStatusRequest;
import com.deliverytracking.dto.PageParams;
import com.deliverytracking.dto.PageResponse;
import com.deliverytracking.dto.PlaceOrderRequest;
import com.deliverytracking.entity.OrderStatus;
import com.deliverytracking.service.OrderService;
import com.deliverytracking.service.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Validated
@Tag(name = "Orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','DELIVERY_PARTNER','ADMIN')")
    @Operation(summary = "List orders (paginated, filterable)",
            description = "Returns orders visible to the authenticated user. Customers see their own orders, "
                    + "delivery partners see the orders they accepted, admins see all orders. Supports pagination "
                    + "(page/size), sorting (sortBy/direction) and filtering by status, customer, delivery partner, "
                    + "restaurant and created-at date range (from/to, yyyy-MM-dd).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Page of orders visible to the caller with pagination metadata"),
            @ApiResponse(responseCode = "400", description = "Invalid query parameter or validation error",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public PageResponse<OrderResponse> getMyOrders(
            @RequestParam(defaultValue = "0") @Min(0)
            @Parameter(description = "Zero-based page number", example = "0") int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100)
            @Parameter(description = "Page size, between 1 and 100", example = "10") int size,
            @RequestParam(required = false)
            @Parameter(description = "Sort field. Allowed: id, status, totalAmount, createdAt, updatedAt",
                    example = "createdAt") String sortBy,
            @RequestParam(defaultValue = "asc")
            @Parameter(description = "Sort direction: asc or desc", example = "desc") String direction,
            @RequestParam(required = false)
            @Parameter(description = "Filter by order status", example = "DELIVERED") OrderStatus status,
            @RequestParam(required = false)
            @Parameter(description = "Filter by customer id", example = "2") Long customer,
            @RequestParam(required = false)
            @Parameter(description = "Filter by delivery partner id", example = "3") Long deliveryPartner,
            @RequestParam(required = false)
            @Parameter(description = "Filter by restaurant id", example = "1") Long restaurant,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(description = "Earliest order date (inclusive), yyyy-MM-dd", example = "2026-08-01")
            LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(description = "Latest order date (inclusive), yyyy-MM-dd", example = "2026-08-05")
            LocalDate to,
            @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.getMyOrders(principal,
                new OrderFilter(status, customer, deliveryPartner, restaurant, from, to),
                new PageParams(page, size, sortBy, direction));
    }

    @GetMapping("/available")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @Operation(summary = "List available orders (paginated)",
            description = "Delivery partner only. Returns orders in PLACED state that can still be accepted. "
                    + "Supports pagination (page/size) and sorting (sortBy/direction); filtering by restaurant id "
                    + "is also possible.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Page of available (PLACED) orders with pagination metadata"),
            @ApiResponse(responseCode = "400", description = "Invalid query parameter or validation error",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but not a DELIVERY_PARTNER",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public PageResponse<OrderResponse> getAvailableOrders(
            @RequestParam(defaultValue = "0") @Min(0)
            @Parameter(description = "Zero-based page number", example = "0") int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100)
            @Parameter(description = "Page size, between 1 and 100", example = "10") int size,
            @RequestParam(required = false)
            @Parameter(description = "Sort field. Allowed: id, status, totalAmount, createdAt, updatedAt",
                    example = "createdAt") String sortBy,
            @RequestParam(defaultValue = "asc")
            @Parameter(description = "Sort direction: asc or desc", example = "desc") String direction,
            @RequestParam(required = false)
            @Parameter(description = "Filter by restaurant id", example = "1") Long restaurant) {
        return orderService.getAvailableOrders(restaurant, new PageParams(page, size, sortBy, direction));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','DELIVERY_PARTNER')")
    @Operation(summary = "Get an order by id",
            description = "Customers can only view their own orders; partners only orders assigned to them.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The requested order",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid path variable or validation error",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Order belongs to a different user",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public OrderResponse getOrder(@PathVariable @Positive Long orderId,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.getOrder(orderId, principal);
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Place a new order",
            description = "Customer only. Captures the delivery destination (address and coordinates) from "
                    + "the request, computes the total from the requested menu items and creates the order in "
                    + "PLACED state.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Order created",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request contains invalid fields (Bean Validation)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but not a CUSTOMER",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Restaurant or menu item not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.placeOrder(request, principal.getId()));
    }

    @PatchMapping("/{orderId}/accept")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @Operation(summary = "Accept an order",
            description = "Delivery partner only. The first partner to accept wins; concurrent accept attempts "
                    + "are rejected with 409 thanks to optimistic locking.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order accepted and assigned to the partner",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid path variable or validation error",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but not a DELIVERY_PARTNER",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Order already accepted or modified concurrently",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<OrderResponse> acceptOrder(@PathVariable @Positive Long orderId,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(orderService.acceptOrder(orderId, principal.getId()));
    }

    @PatchMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Cancel an order",
            description = "Customer only. Cancels the caller's own order while it is still in PLACED state. "
                    + "Once an order has been accepted (or is further along the delivery pipeline) it can no "
                    + "longer be cancelled. Returns the updated order with status CANCELLED.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order cancelled, status is now CANCELLED",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid path variable, or the order cannot be "
                    + "cancelled from its current status (only PLACED → CANCELLED is allowed)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but not a CUSTOMER, or not the "
                    + "customer who placed the order",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Order was modified concurrently",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable @Positive Long orderId,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId, principal.getId()));
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @Operation(summary = "Update order status",
            description = "Delivery partner only. Moves the order along the state machine "
                    + "PLACED → ACCEPTED → PICKED_UP → OUT_FOR_DELIVERY → DELIVERED. ACCEPTED cannot be set "
                    + "through this endpoint (use /accept) and skipped transitions are rejected.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order status updated",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid status transition or validation error",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not the assigned partner or not a DELIVERY_PARTNER",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable @Positive Long orderId,
                                                      @Valid @RequestBody OrderStatusRequest request,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(orderService.updateOrderStatus(orderId, request.getStatus(), principal.getId()));
    }
}

package com.deliverytracking.integration;

import com.deliverytracking.dto.OrderItemRequest;
import com.deliverytracking.dto.OrderStatusRequest;
import com.deliverytracking.dto.PlaceOrderRequest;
import com.deliverytracking.entity.OrderStatus;
import com.deliverytracking.entity.Restaurant;
import com.deliverytracking.entity.Role;
import com.deliverytracking.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaginationFilteringIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RestaurantRepository restaurantRepository;

    private String adminToken;
    private String customerToken;
    private String partnerToken;

    private Long alphaId;
    private Long burgerId;
    private String alphaName;

    private Long order1;
    private Long order2;
    private Long order3;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        customerToken = register(uniqueEmail("pagination-customer"), Role.CUSTOMER);
        partnerToken = register(uniqueEmail("pagination-partner"), Role.DELIVERY_PARTNER);

        String tag = UUID.randomUUID().toString().substring(0, 8);
        alphaName = "Alpha " + tag;
        alphaId = createRestaurant(adminToken, alphaName);
        createRestaurant(adminToken, "Beta " + tag);
        createRestaurant(adminToken, "Gamma " + tag);

        burgerId = addMenuItem(adminToken, alphaId, "Burger", "9.99");
        addMenuItem(adminToken, alphaId, "Pizza", "12.50");
        addMenuItem(adminToken, alphaId, "Noodles", "7.25");

        order1 = placeOrder(customerToken);
        order2 = placeOrder(customerToken);
        order3 = placeOrder(customerToken);
        deliver(order3, partnerToken);
    }

    private Long placeOrder(String token) throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PlaceOrderRequest.builder()
                                .restaurantId(alphaId)
                                .deliveryAddress("42 Pagination St")
                                .deliveryLatitude(18.5204)
                                .deliveryLongitude(73.8567)
                                .items(List.of(OrderItemRequest.builder()
                                        .menuItemId(burgerId)
                                        .quantity(1)
                                        .build()))
                                .build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void deliver(Long orderId, String token) throws Exception {
        mockMvc.perform(patch("/api/orders/{id}/accept", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        updateStatus(orderId, token, "PICKED_UP");
        updateStatus(orderId, token, "OUT_FOR_DELIVERY");
        updateStatus(orderId, token, "DELIVERED");
    }

    private void updateStatus(Long orderId, String token, String status) throws Exception {
        mockMvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(OrderStatusRequest.builder()
                                .status(OrderStatus.valueOf(status))
                                .build())))
                .andExpect(status().isOk());
    }

    @Test
    void restaurants_defaultPage_returnsSpringPageMetadata() throws Exception {
        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.last").isBoolean())
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void restaurants_filterByName_returnsOnlyMatchingRestaurants() throws Exception {
        mockMvc.perform(get("/api/restaurants").param("name", alphaName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value(alphaName));
    }

    @Test
    void restaurants_sortByName_matchesRepositoryOrder() throws Exception {
        List<String> expected = restaurantRepository.findAll().stream()
                .map(Restaurant::getName)
                .sorted(Comparator.comparing(String::toLowerCase, Comparator.reverseOrder()))
                .toList();

        String body = mockMvc.perform(get("/api/restaurants")
                        .param("sortBy", "name")
                        .param("direction", "desc")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode content = objectMapper.readTree(body).path("content");
        assertThat(content.size()).isEqualTo(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            assertThat(content.get(i).path("name").asText()).isEqualTo(expected.get(i));
        }
    }

    @Test
    void restaurants_invalidSortField_returns400() throws Exception {
        mockMvc.perform(get("/api/restaurants").param("sortBy", "doesNotExist"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot sort by 'doesNotExist'"));
    }

    @Test
    void restaurants_invalidDirection_returns400() throws Exception {
        mockMvc.perform(get("/api/restaurants").param("direction", "sideways"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid sort direction: sideways"));
    }

    @Test
    void menu_filterByNameAndPriceRange_returnsOnlyMatchingItems() throws Exception {
        mockMvc.perform(get("/api/restaurants/{id}/menu", alphaId).param("itemName", "bur"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Burger"));

        mockMvc.perform(get("/api/restaurants/{id}/menu", alphaId)
                        .param("minPrice", "10")
                        .param("maxPrice", "13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Pizza"));

        mockMvc.perform(get("/api/restaurants/{id}/menu", alphaId).param("maxPrice", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Noodles"));
    }

    @Test
    void menu_sortByNameAsc_ordersItemsAlphabetically() throws Exception {
        mockMvc.perform(get("/api/restaurants/{id}/menu", alphaId)
                        .param("sortBy", "name")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].name").value("Burger"))
                .andExpect(jsonPath("$.content[1].name").value("Noodles"))
                .andExpect(jsonPath("$.content[2].name").value("Pizza"));
    }

    @Test
    void menu_invalidPriceRange_returns400() throws Exception {
        mockMvc.perform(get("/api/restaurants/{id}/menu", alphaId)
                        .param("minPrice", "15")
                        .param("maxPrice", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("minPrice must not exceed maxPrice"));
    }

    @Test
    void orders_filterByStatus_returnsOnlyMatchingOrders() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .param("status", "PLACED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/orders")
                        .param("status", "DELIVERED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void orders_filterByDateRange_returnsOrdersCreatedToday() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        mockMvc.perform(get("/api/orders")
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void orders_paging_returnsCorrectPageAndMetadata() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content.length()").value(2));

        mockMvc.perform(get("/api/orders")
                        .param("page", "1")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void orders_sortByIdAsc_returnsEarliestOrderFirst() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .param("sortBy", "id")
                        .param("direction", "asc")
                        .param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(order1.intValue()));
    }

    @Test
    void orders_invalidPageSize_returnsValidationErrors() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .param("size", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.size").exists());
    }

    @Test
    void orders_negativePage_returnsValidationErrors() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .param("page", "-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.page").exists());
    }

    @Test
    void availableOrders_partner_seesOnlyPlacedOrders() throws Exception {
        mockMvc.perform(get("/api/orders/available")
                        .param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + partnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(order1.intValue())))
                .andExpect(jsonPath("$.content[*].id", hasItem(order2.intValue())))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(order3.intValue()))));
    }

    @Test
    void locationHistory_customer_returnsEmptyPageWhenNoUpdates() throws Exception {
        mockMvc.perform(get("/api/orders/{id}/location/history", order1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}

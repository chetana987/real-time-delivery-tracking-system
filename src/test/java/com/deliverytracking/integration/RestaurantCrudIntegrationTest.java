package com.deliverytracking.integration;

import com.deliverytracking.dto.MenuItemRequest;
import com.deliverytracking.dto.OrderItemRequest;
import com.deliverytracking.dto.PlaceOrderRequest;
import com.deliverytracking.dto.RestaurantRequest;
import com.deliverytracking.entity.Role;
import com.deliverytracking.repository.MenuItemRepository;
import com.deliverytracking.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RestaurantCrudIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RestaurantRepository restaurantRepository;
    @Autowired
    private MenuItemRepository menuItemRepository;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        customerToken = register(uniqueEmail("restaurant-customer"), Role.CUSTOMER);
    }

    private String restaurantBody(String name) throws Exception {
        return objectMapper.writeValueAsString(RestaurantRequest.builder()
                .name(name)
                .address("1 Test Street")
                .lat(12.34)
                .lng(56.78)
                .build());
    }

    @Test
    void getAllRestaurants_isPubliclyReadable() throws Exception {
        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void getRestaurant_returnsRequestedRestaurant() throws Exception {
        Long id = createRestaurant(adminToken, "Public Kitchen");

        mockMvc.perform(get("/api/restaurants/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Public Kitchen"))
                .andExpect(jsonPath("$.lat").value(12.34))
                .andExpect(jsonPath("$.lng").value(56.78));
    }

    @Test
    void getRestaurant_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/restaurants/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Restaurant not found: 999999"));
    }

    @Test
    void createRestaurant_requiresAdmin() throws Exception {
        mockMvc.perform(post("/api/restaurants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(restaurantBody("Anonymous Kitchen")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/restaurants")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(restaurantBody("Customer Kitchen")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/restaurants")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(restaurantBody("Admin Kitchen")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Admin Kitchen"));
    }

    @Test
    void createRestaurant_blankName_returnsValidationErrors() throws Exception {
        RestaurantRequest request = RestaurantRequest.builder()
                .name(" ")
                .address("1 Test Street")
                .lat(12.34)
                .lng(56.78)
                .build();

        mockMvc.perform(post("/api/restaurants")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void updateRestaurant_admin_updatesFields() throws Exception {
        Long id = createRestaurant(adminToken, "Before Kitchen");
        RestaurantRequest request = RestaurantRequest.builder()
                .name("After Kitchen")
                .address("2 Test Street")
                .lat(1.5)
                .lng(2.5)
                .build();

        mockMvc.perform(put("/api/restaurants/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After Kitchen"));

        assertThat(restaurantRepository.findById(id).orElseThrow().getName()).isEqualTo("After Kitchen");
    }

    @Test
    void deleteRestaurant_admin_removesIt() throws Exception {
        Long id = createRestaurant(adminToken, "Gone Kitchen");

        mockMvc.perform(delete("/api/restaurants/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(restaurantRepository.existsById(id)).isFalse();
    }

    @Test
    void deleteRestaurant_withOrders_returns400() throws Exception {
        Long id = createRestaurant(adminToken, "Busy Kitchen");
        Long menuItemId = addMenuItem(adminToken, id, "Pizza", "12.50");
        String customer = register(uniqueEmail("busy-customer"), Role.CUSTOMER);

        PlaceOrderRequest request = PlaceOrderRequest.builder()
                .restaurantId(id)
                .deliveryAddress("42 Home St")
                .deliveryLatitude(18.5204)
                .deliveryLongitude(73.8567)
                .items(List.of(OrderItemRequest.builder()
                        .menuItemId(menuItemId)
                        .quantity(1)
                        .build()))
                .build();
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/restaurants/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot delete a restaurant that has orders"));
    }

    @Test
    void menu_roundTrip_addGetUpdateDelete() throws Exception {
        Long id = createRestaurant(adminToken, "Menu Kitchen");
        Long itemId = addMenuItem(adminToken, id, "Burger", "9.99");

        mockMvc.perform(get("/api/restaurants/{id}/menu", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Burger"));

        mockMvc.perform(put("/api/restaurants/{id}/menu/{itemId}", id, itemId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MenuItemRequest.builder()
                                .name("Cheeseburger")
                                .price(new BigDecimal("11.49"))
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cheeseburger"));

        mockMvc.perform(delete("/api/restaurants/{id}/menu/{itemId}", id, itemId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(menuItemRepository.existsById(itemId)).isFalse();
    }

    @Test
    void addMenuItem_zeroPrice_returnsValidationErrors() throws Exception {
        Long id = createRestaurant(adminToken, "Validation Kitchen");

        mockMvc.perform(post("/api/restaurants/{id}/menu", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MenuItemRequest.builder()
                                .name("Free Item")
                                .price(new BigDecimal("0.00"))
                                .build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price").exists());
    }
}

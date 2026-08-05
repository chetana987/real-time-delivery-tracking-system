package com.deliverytracking.service;

import com.deliverytracking.dto.MenuItemRequest;
import com.deliverytracking.dto.MenuItemResponse;
import com.deliverytracking.dto.PageParams;
import com.deliverytracking.dto.PageResponse;
import com.deliverytracking.dto.RestaurantRequest;
import com.deliverytracking.dto.RestaurantResponse;
import com.deliverytracking.entity.MenuItem;
import com.deliverytracking.entity.Restaurant;
import com.deliverytracking.exception.BadRequestException;
import com.deliverytracking.exception.ResourceNotFoundException;
import com.deliverytracking.repository.MenuItemRepository;
import com.deliverytracking.repository.MenuItemSpecs;
import com.deliverytracking.repository.OrderRepository;
import com.deliverytracking.repository.RestaurantRepository;
import com.deliverytracking.repository.RestaurantSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final OrderRepository orderRepository;

    private static final Set<String> RESTAURANT_SORT_FIELDS = Set.of("id", "name", "address", "lat", "lng");
    private static final Set<String> MENU_SORT_FIELDS = Set.of("id", "name", "price");

    @Transactional(readOnly = true)
    public PageResponse<RestaurantResponse> getAllRestaurants(String name, PageParams params) {
        Specification<Restaurant> spec = Specification.where(name != null && !name.isBlank()
                ? RestaurantSpecs.nameContains(name) : null);
        Pageable pageable = PagingSupport.pageRequest(params, RESTAURANT_SORT_FIELDS, "id", "asc");
        return PageResponse.from(restaurantRepository.findAll(spec, pageable).map(RestaurantResponse::from));
    }

    @Transactional(readOnly = true)
    public RestaurantResponse getRestaurant(Long id) {
        return RestaurantResponse.from(findRestaurant(id));
    }

    @Transactional
    public RestaurantResponse createRestaurant(RestaurantRequest request) {
        Restaurant restaurant = Restaurant.builder()
                .name(request.getName())
                .address(request.getAddress())
                .lat(request.getLat())
                .lng(request.getLng())
                .build();
        return RestaurantResponse.from(restaurantRepository.save(restaurant));
    }

    @Transactional
    public RestaurantResponse updateRestaurant(Long id, RestaurantRequest request) {
        Restaurant restaurant = findRestaurant(id);
        restaurant.setName(request.getName());
        restaurant.setAddress(request.getAddress());
        restaurant.setLat(request.getLat());
        restaurant.setLng(request.getLng());
        return RestaurantResponse.from(restaurantRepository.save(restaurant));
    }

    @Transactional
    public void deleteRestaurant(Long id) {
        Restaurant restaurant = findRestaurant(id);
        if (orderRepository.existsByRestaurantId(id)) {
            throw new BadRequestException("Cannot delete a restaurant that has orders");
        }
        restaurantRepository.delete(restaurant);
    }

    @Transactional(readOnly = true)
    public PageResponse<MenuItemResponse> getMenu(Long restaurantId, String itemName, BigDecimal minPrice,
                                                  BigDecimal maxPrice, PageParams params) {
        findRestaurant(restaurantId);

        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("minPrice must not exceed maxPrice");
        }

        Specification<MenuItem> spec = Specification.where(MenuItemSpecs.restaurantId(restaurantId));
        if (itemName != null && !itemName.isBlank()) {
            spec = spec.and(MenuItemSpecs.nameContains(itemName));
        }
        if (minPrice != null) {
            spec = spec.and(MenuItemSpecs.priceAtLeast(minPrice));
        }
        if (maxPrice != null) {
            spec = spec.and(MenuItemSpecs.priceAtMost(maxPrice));
        }

        Pageable pageable = PagingSupport.pageRequest(params, MENU_SORT_FIELDS, "id", "asc");
        return PageResponse.from(menuItemRepository.findAll(spec, pageable).map(MenuItemResponse::from));
    }

    @Transactional
    public MenuItemResponse addMenuItem(Long restaurantId, MenuItemRequest request) {
        Restaurant restaurant = findRestaurant(restaurantId);
        MenuItem item = MenuItem.builder()
                .name(request.getName())
                .price(request.getPrice())
                .build();
        restaurant.addMenuItem(item);
        return MenuItemResponse.from(menuItemRepository.save(item));
    }

    @Transactional
    public MenuItemResponse updateMenuItem(Long restaurantId, Long itemId, MenuItemRequest request) {
        MenuItem item = findMenuItem(restaurantId, itemId);
        item.setName(request.getName());
        item.setPrice(request.getPrice());
        return MenuItemResponse.from(menuItemRepository.save(item));
    }

    @Transactional
    public void deleteMenuItem(Long restaurantId, Long itemId) {
        menuItemRepository.delete(findMenuItem(restaurantId, itemId));
    }

    private Restaurant findRestaurant(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + id));
    }

    private MenuItem findMenuItem(Long restaurantId, Long itemId) {
        MenuItem item = menuItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found: " + itemId));
        if (!item.getRestaurant().getId().equals(restaurantId)) {
            throw new ResourceNotFoundException("Menu item not found: " + itemId);
        }
        return item;
    }
}

package com.deliverytracking.config;

import com.deliverytracking.entity.Role;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.security.Principal;

@Getter
@RequiredArgsConstructor
public class StompPrincipal implements Principal {

    private final Long userId;
    private final String email;
    private final Role role;

    @Override
    public String getName() {
        return email;
    }
}

package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.AddToCartRequest;
import org.example.dto.CartDto;
import org.example.dto.UpdateCartItemRequest;
import org.example.service.CartService;
import org.example.common.CurrentUser;
import org.example.shared.api.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartDto> getCart(@AuthenticationPrincipal AuthenticatedUser user) {
        long userId = CurrentUser.id(user);
        return ResponseEntity.ok(cartService.getCart(userId));
    }

    @PostMapping("/items")
    public ResponseEntity<CartDto> addToCart(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AddToCartRequest request) {
        long userId = CurrentUser.id(user);
        return ResponseEntity.ok(cartService.addToCart(userId, request));
    }

    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<CartDto> updateCartItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        long userId = CurrentUser.id(user);
        return ResponseEntity.ok(cartService.updateCartItem(userId, cartItemId, request));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<CartDto> removeCartItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long cartItemId) {
        long userId = CurrentUser.id(user);
        return ResponseEntity.ok(cartService.removeCartItem(userId, cartItemId));
    }
}

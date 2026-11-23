package org.example.dto;

import java.util.List;

public record CartDto(Long id, Long userId, List<CartItemDto> items) {}

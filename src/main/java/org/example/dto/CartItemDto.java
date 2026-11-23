package org.example.dto;


public record CartItemDto(Long id, FoodDto food, Integer quantity) {}

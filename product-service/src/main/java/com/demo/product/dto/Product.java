package com.demo.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    private Long   id;
    private String name;
    private String description;
    private Double price;
    private String category;
}

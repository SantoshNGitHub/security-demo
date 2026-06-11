package com.demo.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductRequest {

    @NotBlank
    @Size(min = 2, max = 100)
    private String name;

    @NotBlank
    @Size(max = 500)
    private String description;

    @NotNull
    @Positive
    private Double price;

    @NotBlank
    @Size(max = 50)
    private String category;
}

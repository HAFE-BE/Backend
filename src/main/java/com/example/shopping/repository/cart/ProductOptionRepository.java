package com.example.shopping.repository.cart;

import com.example.shopping.domain.Product;
import com.example.shopping.domain.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Integer> {
    public Optional<ProductOption> findByProductIdAndOptionId(Integer productId, Integer optionId);
    Optional<ProductOption> findByProduct(Product product);
}
package com.example.shopping.domain;

import com.example.shopping.domain.common.BaseTimeEntity;
import lombok.*;

import javax.persistence.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "carts")
public class Cart extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(referencedColumnName = "id",name = "product_option_id", nullable = false)
    private ProductOption productOption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(referencedColumnName = "id", name = "user_id", nullable = false)
    private User user;

    private boolean isDelete;

    private Integer count;

    @Override
    public String toString() {
        return "Cart{" +
                "id=" + id +
                ", productOption=" + productOption +
                ", isDelete=" + isDelete +
                ", count=" + count +
                '}';
    }
}

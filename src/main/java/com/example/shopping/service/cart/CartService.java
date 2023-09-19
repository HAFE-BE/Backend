package com.example.shopping.service.cart;

import com.example.shopping.domain.*;
import com.example.shopping.domain.Enum.RequestType;
import com.example.shopping.dto.cart.*;
import com.example.shopping.dto.common.CommonResponse;
import com.example.shopping.repository.address.AddressRepository;
import com.example.shopping.repository.cart.CartRepository;
import com.example.shopping.repository.cart.ProductOptionRepository;
import com.example.shopping.repository.delivery.DeliveryRepository;
import com.example.shopping.repository.order.OrderRepository;
import com.example.shopping.repository.pay.PayRepository;
import com.example.shopping.repository.product.ProductRepository;
import com.example.shopping.repository.user.UserRepository;
import com.example.shopping.service.error.ErrorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService implements CartServiceInterface {
    private final ErrorService errorService;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final AddressRepository addressRepository;
    private final DeliveryRepository deliveryRepository;
    private final PayRepository payRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public CommonResponse addCart(Integer productId, AddCartRequest addCartRequest, Integer userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        User user = userOptional.get();

        Optional<Product> productOptional = productRepository.findById(productId);
        if (productOptional.isEmpty()) {
            return errorService.createErrorResponse("해당 상품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND, null);
        }
        Product product = productOptional.get();

        Optional<ProductOption> productOption = this.findOptionById(product, addCartRequest.getOptionId());

        if(productOption.isEmpty()) return errorService.createErrorResponse("해당 옵션을 찾을 수 없습니다.", HttpStatus.BAD_REQUEST, null);
        if(productOption.isPresent() &&  productOption.get().getStock() < addCartRequest.getCount()) return errorService.createErrorResponse("재고가 부족합니다.", HttpStatus.BAD_REQUEST, null);

        Optional<Cart> existedCart = cartRepository.findByIdAndUserIdAndIsDeleteFalse(productOption.get().getId(), user.getId());
        if(existedCart.isPresent()) {
            return errorService.createErrorResponse("이미 등록된 장바구니 상품입니다.", HttpStatus.BAD_REQUEST, null);
        } else {
            Cart cart = new Cart()
                    .builder()
                    .user(user)
                    .count(addCartRequest.getCount())
                    .productOption(productOption.get())
                    .build();
            cartRepository.save(cart);
            return errorService.createSuccessResponse("장바구니 등록 완료했습니다.", HttpStatus.CREATED, null);
        }
    }

    public CommonResponse updateCart(Integer cartId, UpdatedCartRequest updateCartRequest, Integer userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        User user = userOptional.get();

        Optional<Cart> cartOptional = cartRepository.findById(cartId);
        if(cartOptional.get().getProductOption().getProduct().getId() != updateCartRequest.getProductId()) {
            return errorService.createErrorResponse("장바구니 내 상품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND, null);
        } else if(cartOptional.isPresent()) {
            Optional<Product> productOptional = productRepository.findById(updateCartRequest.getProductId());
            Product product = productOptional.get();

            Optional<ProductOption> productOption = productOptionRepository.findByProductIdAndOptionId(product.getId(), updateCartRequest.getOptionId());

            if(productOption.isEmpty()) return errorService.createErrorResponse("해당 옵션을 찾을 수 없습니다.", HttpStatus.BAD_REQUEST, null);
            if(productOption.isPresent() &&  productOption.get().getStock() < updateCartRequest.getCount()) return errorService.createErrorResponse("재고가 부족합니다.", HttpStatus.BAD_REQUEST, null);

            Cart cart = cartOptional.get();
            cart.setProductOption(cartOptional.get().getProductOption());
            cart.setCount(updateCartRequest.getCount());
            cart.setDelete(updateCartRequest.getIsDelete());
            cart.setUser(user);
            cartRepository.save(cart);
        }
        return errorService.createSuccessResponse(String.format("장바구니 %s 완료했습니다", updateCartRequest.getIsDelete() ? "삭제" : "수정"), HttpStatus.OK, null);
    }


    @Transactional
    public CommonResponse orderCart(List<Integer> cartIds, OrderCartRequest orderCartRequest, Integer userId) {
        Pay pay = payRepository.findByUserId(userId);

        Delivery delivery = new Delivery();

        List<OrderCartResponse> cart = cartRepository.findAllProductByIds(cartIds);
        // 구매하고자하는 장바구니 목록 존재하지 않을 때
        if(cart.size() == 0) return errorService.createErrorResponse("주문하려는 장바구니들을 찾을 수 없습니다.", HttpStatus.NOT_FOUND, null);

        int totalPrice = cart.stream()
                .mapToInt(order -> order.getPrice() * order.getCart().getCount())
                .sum();

        // 구매하고자하는 장바구니에 제품 총 가격이 가지고 있는 금액보다 클 때
        if(cart.size() > 0 && pay.getAmount() < totalPrice) {
            return errorService.createErrorResponse("금액이 부족합니다.", HttpStatus.BAD_REQUEST, null);
        } else {
            List<Address> existedAddress = addressRepository.findAllByUserId(1);
            Optional<Address> defaultAddress = existedAddress.stream().filter(a -> a.getIsDefault() == true).findFirst();
            // 이미 등록된 주소와 배송 정보가 있을 때
            if(defaultAddress.isPresent() &&
                    orderCartRequest.getRecipient() == null &&
                    orderCartRequest.getContactA() == null &&
                    orderCartRequest.getZipCode() == null &&
                    orderCartRequest.getMainAddress() == null
            ){
                delivery = deliveryRepository.findByAddressId(defaultAddress.get().getId());
            } else {
                Address newAddress = new Address();
                if(orderCartRequest.getRecipient() == null ||
                        orderCartRequest.getContactA() == null ||
                        orderCartRequest.getZipCode() == null ||
                        orderCartRequest.getMainAddress() == null) return errorService.createErrorResponse("배송정보 신규 등록시 수령인, 우편번호, 메인주소, 연락처는 필수 입니다", HttpStatus.BAD_REQUEST, null);
                if(existedAddress.size() >= 5) return errorService.createErrorResponse("최대 등록할 수 있는 주소는 5개입니다.", HttpStatus.BAD_REQUEST, null);
                User user = userRepository.findById(1).orElseThrow(() -> new IllegalArgumentException("해당 유저 발견할 수 없음"));
                // 새로운 주소와 배송정보로 등록할 때
                newAddress.setMain(orderCartRequest.getMainAddress());
                newAddress.setDetail(orderCartRequest.getDetailAddress());
                newAddress.setZieCode(orderCartRequest.getZipCode());
                newAddress.setIsDefault(orderCartRequest.getIsDefault());
                newAddress.setIsOrder(true);
                newAddress.setUser(user);

                delivery.setName(orderCartRequest.getDeliveryName());
                delivery.setRecipient(orderCartRequest.getRecipient());
                delivery.setContactA(orderCartRequest.getContactA());
                delivery.setContactB(orderCartRequest.getContactB());
                delivery.setRequest(orderCartRequest.getRequestType() == RequestType.CUSTOM_INPUT ? orderCartRequest.getCustomRequest()  : orderCartRequest.getRequestType() .getDescription());
                delivery.setAddress(newAddress);

                if(orderCartRequest.getIsDefault()){
                    defaultAddress.get().setIsDefault(false);
                    addressRepository.save(defaultAddress.get());
                }
                addressRepository.save(newAddress);
                deliveryRepository.save(delivery);
            }

            for (int idx = 0; idx < cart.size() ; idx++){
                Order order = new Order();
                order.setCart(cart.get(idx).getCart());
                order.setCount(cart.get(idx).getCart().getCount());
                order.setDelivery(delivery);
                orderRepository.save(order);

                ProductOption productOption = cart.get(idx).getCart().getProductOption();
                productOption.setStock(productOption.getStock() - cart.get(idx).getCart().getCount());
                productOptionRepository.save(productOption);

                cart.get(idx).getCart().setDelete(true);
                cartRepository.save(cart.get(idx).getCart());
            }
                pay.setAmount(pay.getAmount() - totalPrice);
                payRepository.save(pay);
        }
        return errorService.createErrorResponse("주문 완료했습니다.", HttpStatus.CREATED, null);
    }

    private Optional<ProductOption> findOptionById(Product product, Integer optionId) {
        return product.getProductOptions().stream()
                .filter(option -> option.getOption().getId().equals(optionId))
                .findFirst();
    }
}

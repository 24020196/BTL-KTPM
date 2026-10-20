package com.test.ktpm.Services;

import com.test.ktpm.Dto.Request.OrderCreateRequest;
import com.test.ktpm.Dto.Request.OrderItemRequest;
import com.test.ktpm.Dto.Response.OrderResponse;
import com.test.ktpm.Entity.*;
import com.test.ktpm.Exceptions.BadRequestException;
import com.test.ktpm.Exceptions.ResourceNotFoundException;
import com.test.ktpm.Mapper.OrderMapper;
import com.test.ktpm.Repositories.CustomerRepository;
import com.test.ktpm.Repositories.OrderRepository;
import com.test.ktpm.Repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable)
                .map(orderMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã: " + id));
        return orderMapper.toResponse(order);
    }

    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng với mã: " + request.getCustomerNumber()));

        Order order = Order.builder()
                .customer(customer)
                .orderDate(LocalDateTime.now())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .orderDetails(new ArrayList<>())
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với mã: " + itemRequest.getProductCode()));

            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BadRequestException("Sản phẩm '" + product.getProductName() + "' (mã: " + product.getProductCode() + ") hiện không hoạt động kinh doanh");
            }

            product.decreaseStock(itemRequest.getQuantityOrdered());
            productRepository.save(product);

            BigDecimal priceEach = product.getBuyPrice();
            BigDecimal subtotal = priceEach.multiply(BigDecimal.valueOf(itemRequest.getQuantityOrdered()));
            totalAmount = totalAmount.add(subtotal);

            OrderDetailId detailId = OrderDetailId.builder()
                    .productCode(product.getProductCode())
                    .build();

            OrderDetail detail = OrderDetail.builder()
                    .id(detailId)
                    .order(order)
                    .product(product)
                    .quantityOrdered(itemRequest.getQuantityOrdered())
                    .priceEach(priceEach)
                    .build();

            order.addOrderDetail(detail);
        }

        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);
        return orderMapper.toResponse(savedOrder);
    }

    @Transactional
    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng để xóa với mã: " + id));

        for (OrderDetail detail : order.getOrderDetails()) {
            Product product = detail.getProduct();
            product.increaseStock(detail.getQuantityOrdered());
            productRepository.save(product);
        }

        orderRepository.delete(order);
    }
}

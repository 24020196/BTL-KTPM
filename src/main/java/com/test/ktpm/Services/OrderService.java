package com.test.ktpm.Services;

import com.test.ktpm.Model.Request.OrderCreateRequest;
import com.test.ktpm.Model.Request.OrderItemRequest;
import com.test.ktpm.Model.Request.OrderPatchRequest;
import com.test.ktpm.Model.Response.OrderResponse;
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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final ProductService productService;
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
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy khách hàng với mã: " + request.getCustomerNumber()));

        // 1. Lock Ordering chống Deadlock: Lấy danh sách productCode duy nhất và sắp
        // xếp tăng dần
        List<Long> productCodes = request.getItems().stream()
                .map(OrderItemRequest::getProductCode)
                .distinct()
                .sorted()
                .toList();

        // 2. Khóa bi quan (SELECT ... FOR UPDATE) theo thứ tự ID tăng dần
        List<Product> lockedProducts = productRepository.findAllByIdWithLock(productCodes);
        Map<Long, Product> productMap = lockedProducts.stream()
                .collect(Collectors.toMap(Product::getProductCode, Function.identity()));

        // Kiểm tra tồn tại
        for (Long code : productCodes) {
            if (!productMap.containsKey(code)) {
                throw new ResourceNotFoundException("Không tìm thấy sản phẩm với mã: " + code);
            }
        }

        Order order = Order.builder()
                .customer(customer)
                .orderDate(LocalDateTime.now())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .orderDetails(new ArrayList<>())
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = productMap.get(itemRequest.getProductCode());

            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BadRequestException("Sản phẩm '" + product.getProductName() + "' (mã: "
                        + product.getProductCode() + ") hiện không hoạt động kinh doanh");
            }

            productService.decreaseStock(product, itemRequest.getQuantityOrdered());

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
    public OrderResponse patchOrder(Long id, OrderPatchRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy đơn hàng với mã: " + id));

        // 1. Đơn hàng đã ở trạng thái CANCELLED hoặc COMPLETED thì không cho phép chỉnh
        // sửa
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.COMPLETED) {
            throw new BadRequestException(
                    "Không thể chỉnh sửa đơn hàng đã ở trạng thái: " + order.getStatus());
        }

        // 2. Chuyển trạng thái sang CANCELLED: hoàn kho toàn bộ sản phẩm và không cho
        // phép đổi items
        if (request.getStatus() == OrderStatus.CANCELLED) {
            if (request.getItems() != null && !request.getItems().isEmpty()) {
                throw new BadRequestException("Không thể cập nhật danh sách sản phẩm khi hủy đơn hàng");
            }
            List<Long> productCodes = order.getOrderDetails().stream()
                    .map(d -> d.getProduct().getProductCode())
                    .distinct()
                    .sorted()
                    .toList();

            List<Product> lockedProducts = productCodes.isEmpty()
                    ? Collections.emptyList()
                    : productRepository.findAllByIdWithLock(productCodes);
            Map<Long, Product> productMap = lockedProducts.stream()
                    .collect(Collectors.toMap(Product::getProductCode, Function.identity()));

            for (OrderDetail detail : order.getOrderDetails()) {
                Product product = productMap.get(detail.getProduct().getProductCode());
                if (product != null) {
                    productService.increaseStock(product, detail.getQuantityOrdered());
                }
            }
            order.setStatus(OrderStatus.CANCELLED);
            return orderMapper.toResponse(orderRepository.save(order));
        }

        boolean isPending = order.getStatus() == OrderStatus.PENDING;

        // 3. Nếu không phải PENDING (ví dụ: SHIPPING): chỉ cho phép cập nhật status
        if (!isPending) {
            if (request.getItems() != null && !request.getItems().isEmpty()) {
                throw new BadRequestException(
                        "Không thể thay đổi sản phẩm khi đơn hàng đã ở trạng thái: " + order.getStatus());
            }
            if (request.getStatus() != null) {
                order.setStatus(request.getStatus());
            }
            return orderMapper.toResponse(orderRepository.save(order));
        }

        // 4. PENDING: cho phép cập nhật status + items
        if (request.getStatus() != null) {
            order.setStatus(request.getStatus());
        }

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            // Tập hợp tất cả productCodes (cả sản phẩm cũ cần hoàn và sản phẩm mới cần trừ)
            Set<Long> allProductCodes = new HashSet<>();
            for (OrderDetail detail : order.getOrderDetails()) {
                allProductCodes.add(detail.getProduct().getProductCode());
            }
            for (OrderItemRequest itemRequest : request.getItems()) {
                allProductCodes.add(itemRequest.getProductCode());
            }

            List<Long> sortedCodes = allProductCodes.stream().sorted().toList();
            List<Product> lockedProducts = productRepository.findAllByIdWithLock(sortedCodes);
            Map<Long, Product> productMap = lockedProducts.stream()
                    .collect(Collectors.toMap(Product::getProductCode, Function.identity()));

            // Kiểm tra các sản phẩm mới có tồn tại không
            for (OrderItemRequest itemRequest : request.getItems()) {
                if (!productMap.containsKey(itemRequest.getProductCode())) {
                    throw new ResourceNotFoundException(
                            "Không tìm thấy sản phẩm với mã: " + itemRequest.getProductCode());
                }
            }

            // Hoàn kho cho items cũ
            for (OrderDetail detail : order.getOrderDetails()) {
                Product product = productMap.get(detail.getProduct().getProductCode());
                if (product != null) {
                    productService.increaseStock(product, detail.getQuantityOrdered());
                }
            }

            // Xóa items cũ
            order.getOrderDetails().clear();

            // Tạo items mới & trừ kho
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (OrderItemRequest itemRequest : request.getItems()) {
                Product product = productMap.get(itemRequest.getProductCode());

                if (product.getStatus() != ProductStatus.ACTIVE) {
                    throw new BadRequestException(
                            "Sản phẩm '" + product.getProductName()
                                    + "' hiện không hoạt động kinh doanh");
                }

                productService.decreaseStock(product, itemRequest.getQuantityOrdered());

                BigDecimal priceEach = product.getBuyPrice();
                BigDecimal subtotal = priceEach.multiply(
                        BigDecimal.valueOf(itemRequest.getQuantityOrdered()));
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
        }

        Order savedOrder = orderRepository.save(order);
        return orderMapper.toResponse(savedOrder);
    }
}

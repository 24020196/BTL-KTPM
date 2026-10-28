package com.test.ktpm.Concurrency;

import com.test.ktpm.BaseIntegrationTest;
import com.test.ktpm.Entity.Customer;
import com.test.ktpm.Entity.Product;
import com.test.ktpm.Exceptions.BadRequestException;
import com.test.ktpm.Model.Request.OrderCreateRequest;
import com.test.ktpm.Services.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Kiểm thử Race Condition: Chống bán quá tồn kho (Prevent Overselling)")
public class OversellingConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        testData.cleanDatabase();
    }

    /**
     * KỊCH BẢN 1: Chống bán quá tồn kho (Overselling)
     * - Tồn kho ban đầu: 10 sản phẩm
     * - 10 luồng đồng thời tranh chấp mua, mỗi luồng mua 2 sản phẩm (nhu cầu: 20 sản phẩm)
     * - Kỳ vọng: Đúng 5 luồng thành công, 5 luồng bị chặn bởi ngoại lệ "Không đủ tồn kho"
     * - Tồn kho cuối cùng: chính xác = 0 (không bị âm)
     */
    @Test
    @DisplayName("10 luồng đồng thời mua sản phẩm (tồn kho 10, mua 2/đơn) -> Đúng 5 đơn thành công, tồn kho về 0")
    void testConcurrency_PreventOverselling() throws InterruptedException {
        // 1. Chuẩn bị dữ liệu bằng TestDataHelper
        Product product = testData.createDefaultProduct("Sản phẩm Hot Giảm Giá", 10);
        Customer customer = testData.createCustomer("Khách săn sale", "0988776655", null);

        int numberOfThreads = 10;
        int quantityPerOrder = 2;

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            futures.add(executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    // Đợi tất cả luồng sẵn sàng để kích hoạt đồng thời
                    startLatch.await();

                    OrderCreateRequest request = testData.createOrderRequest(
                            customer.getCustomerNumber(),
                            List.of(testData.createOrderItem(product.getProductCode(), quantityPerOrder))
                    );

                    orderService.createOrder(request);
                    successCount.incrementAndGet();
                } catch (BadRequestException ex) {
                    failCount.incrementAndGet();
                } catch (Exception ex) {
                    failCount.incrementAndGet();
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Kích nổ đồng thời

        for (Future<?> f : futures) {
            try {
                f.get(15, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }

        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        // 2. Xác thực kết quả
        assertThat(successCount.get())
                .as("Số đơn hàng thành công phải chính xác là 5 (10 tồn kho / 2 mỗi đơn)")
                .isEqualTo(5);

        assertThat(failCount.get())
                .as("Số lần thất bại do hết tồn kho phải chính xác là 5")
                .isEqualTo(5);

        Product updatedProduct = testData.getProductRepository().findById(product.getProductCode()).orElseThrow();
        assertThat(updatedProduct.getQuantityInStock())
                .as("Tồn kho sau các giao dịch tranh chấp phải bằng 0")
                .isEqualTo(0);

        long totalOrdersCreated = testData.getOrderRepository().count();
        assertThat(totalOrdersCreated)
                .as("Tổng số đơn hàng thực tế trong DB phải là 5")
                .isEqualTo(5);
    }
}

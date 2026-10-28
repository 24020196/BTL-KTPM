package com.test.ktpm.Concurrency;

import com.test.ktpm.BaseIntegrationTest;
import com.test.ktpm.Entity.Customer;
import com.test.ktpm.Entity.Product;
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

@DisplayName("Kiểm thử Race Condition: Chống Deadlock nhờ sắp xếp thứ tự khóa (Lock Ordering)")
public class DeadlockConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        testData.cleanDatabase();
    }

    /**
     * KỊCH BẢN 2: Tránh Deadlock khi nhiều giao dịch tranh chấp nhiều bản ghi theo thứ tự ngược nhau
     * - Sản phẩm A (code nhỏ) và Sản phẩm B (code lớn), tồn kho mỗi loại = 20
     * - Luồng nhóm 1: Mua [A, B]
     * - Luồng nhóm 2: Mua [B, A]
     * - Nhờ OrderService sắp xếp thứ tự các mã trước khi Lock (SELECT ... FOR UPDATE), không xảy ra Deadlock
     */
    @Test
    @DisplayName("10 luồng mua chéo [A, B] và [B, A] đồng thời -> Không deadlock, 10/10 thành công")
    void testConcurrency_DeadlockPrevention() throws InterruptedException {
        // 1. Chuẩn bị 2 sản phẩm và khách hàng bằng TestDataHelper
        Product productA = testData.createDefaultProduct("Sản phẩm A", 20);
        Product productB = testData.createDefaultProduct("Sản phẩm B", 20);
        Customer customer = testData.createCustomer("Khách hàng VIP", "0912345678", null);

        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger deadlockOrErrorCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            final boolean orderAFirst = (i % 2 == 0);
            futures.add(executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();

                    OrderCreateRequest request;
                    if (orderAFirst) {
                        request = testData.createOrderRequest(
                                customer.getCustomerNumber(),
                                List.of(
                                        testData.createOrderItem(productA.getProductCode(), 1),
                                        testData.createOrderItem(productB.getProductCode(), 1)
                                )
                        );
                    } else {
                        request = testData.createOrderRequest(
                                customer.getCustomerNumber(),
                                List.of(
                                        testData.createOrderItem(productB.getProductCode(), 1),
                                        testData.createOrderItem(productA.getProductCode(), 1)
                                )
                        );
                    }

                    orderService.createOrder(request);
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    deadlockOrErrorCount.incrementAndGet();
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<?> f : futures) {
            try {
                f.get(15, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }

        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        // 2. Xác thực
        assertThat(deadlockOrErrorCount.get())
                .as("Không được có bất kỳ lỗi hoặc Deadlock nào xảy ra")
                .isEqualTo(0);

        assertThat(successCount.get())
                .as("Tất cả 10 đơn hàng đều phải hoàn thành thành công")
                .isEqualTo(10);

        Product updatedA = testData.getProductRepository().findById(productA.getProductCode()).orElseThrow();
        Product updatedB = testData.getProductRepository().findById(productB.getProductCode()).orElseThrow();

        assertThat(updatedA.getQuantityInStock()).isEqualTo(10);
        assertThat(updatedB.getQuantityInStock()).isEqualTo(10);
    }
}


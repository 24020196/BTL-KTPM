package com.test.ktpm.Concurrency;

import com.test.ktpm.BaseIntegrationTest;
import com.test.ktpm.Entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Kiểm thử Race Condition: Khóa lạc quan (Optimistic Locking với @Version)")
public class OptimisticLockingConcurrencyTest extends BaseIntegrationTest {

    @BeforeEach
    void setUp() {
        testData.cleanDatabase();
    }

    /**
     * KỊCH BẢN 3: Optimistic Locking ngăn chặn Lost Update bằng trường @Version
     * - Luồng 1 và Luồng 2 cùng đọc sản phẩm ở Version 0
     * - Luồng 1 cập nhật thành công -> Version tăng lên 1
     * - Luồng 2 cố gắng lưu bản ghi mang Version cũ 0 -> Bị từ chối với ObjectOptimisticLockingFailureException
     */
    @Test
    @DisplayName("2 luồng sửa cùng 1 thực thể -> Luồng thứ 2 bị văng ObjectOptimisticLockingFailureException")
    void testConcurrency_OptimisticLocking() throws Exception {
        // 1. Chuẩn bị sản phẩm bằng TestDataHelper
        Product product = testData.createDefaultProduct("Sản phẩm test Optimistic Lock", 10);
        Long productCode = product.getProductCode();

        // Đọc thực thể Product vào 2 phiên làm việc riêng biệt (mô phỏng 2 transaction độc lập)
        Product instance1 = testData.getProductRepository().findById(productCode).orElseThrow();
        Product instance2 = testData.getProductRepository().findById(productCode).orElseThrow();

        // Xác nhận ban đầu cả 2 đều thấy cùng version (thường là 0)
        assertThat(instance1.getVersion()).isEqualTo(instance2.getVersion());

        // Luồng 1: Cập nhật và lưu thành công -> DB version sẽ tăng lên version + 1
        instance1.setQuantityInStock(8);
        testData.getProductRepository().saveAndFlush(instance1);

        // Luồng 2: Thực hiện lưu thực thể với version cũ trên một luồng khác
        AtomicBoolean optimisticLockFailed = new AtomicBoolean(false);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<?> future = executor.submit(() -> {
            try {
                instance2.setQuantityInStock(5);
                testData.getProductRepository().saveAndFlush(instance2);
            } catch (ObjectOptimisticLockingFailureException ex) {
                optimisticLockFailed.set(true);
            }
        });

        future.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        // 2. Xác thực Luồng 2 phát hiện xung đột và ném ngoại lệ
        assertThat(optimisticLockFailed.get())
                .as("Thao tác cập nhật với Version cũ phải ném ObjectOptimisticLockingFailureException")
                .isTrue();

        // Dữ liệu cuối cùng phải giữ nguyên giá trị hợp lệ của Luồng 1 (stock = 8)
        Product finalProduct = testData.getProductRepository().findById(productCode).orElseThrow();
        assertThat(finalProduct.getQuantityInStock()).isEqualTo(8);
    }
}


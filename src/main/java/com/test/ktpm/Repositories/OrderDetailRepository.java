package com.test.ktpm.Repositories;

import com.test.ktpm.Entity.OrderDetail;
import com.test.ktpm.Entity.OrderDetailId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, OrderDetailId> {
}

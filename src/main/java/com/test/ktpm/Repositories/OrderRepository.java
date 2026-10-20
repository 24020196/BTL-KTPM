package com.test.ktpm.Repositories;

import com.test.ktpm.Entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o WHERE o.employee.employeeNumber = :employeeNumber")
    Page<Order> findByEmployeeNumber(@Param("employeeNumber") Long employeeNumber, Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderDetails WHERE o.employee.employeeNumber = :employeeNumber")
    List<Order> findAllByEmployeeNumberWithDetails(@Param("employeeNumber") Long employeeNumber);

    List<Order> findByCustomerCustomerNumber(Long customerNumber);
}

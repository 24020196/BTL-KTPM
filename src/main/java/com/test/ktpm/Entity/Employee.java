package com.test.ktpm.Entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_number")
    private Long employeeNumber;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(name = "employee_name", nullable = false)
    private String employeeName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reports_to")
    private Employee reportsTo;
}

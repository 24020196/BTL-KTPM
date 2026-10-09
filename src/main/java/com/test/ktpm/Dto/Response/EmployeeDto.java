package com.test.ktpm.Dto.Response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeDto {
    private Long employeeNumber;
    private String employeeName;
    private Long reportsTo;
    private String reportsToName;
    private Long userId;
    private String username;
}

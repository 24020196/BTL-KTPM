package com.test.ktpm.Dto.Request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeRequest {

    @NotBlank(message = "Tên nhân viên không được để trống")
    private String employeeName;

    @JsonAlias({"reports_to", "reportsToEmployeeNumber"})
    private Long reportsTo;

    @JsonAlias({"user_id"})
    private Long userId;
}

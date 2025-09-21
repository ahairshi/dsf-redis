package com.company.entitlement.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import javax.validation.constraints.NotBlank;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EntitlementRequest {
    
    @NotBlank(message = "Username cannot be blank")
    private String username;
    
    @NotBlank(message = "IBD cannot be blank")
    private String ibd;
    
    @NotBlank(message = "Product code cannot be blank")
    private String productCode;
}

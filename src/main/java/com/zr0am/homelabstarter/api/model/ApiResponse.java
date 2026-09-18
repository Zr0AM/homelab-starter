package com.zr0am.homelabstarter.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.Instant;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse {
    private Instant timestamp;
    private String requestId;
    private Integer status;
    private String error;
    private String message;
    private Object response;
}

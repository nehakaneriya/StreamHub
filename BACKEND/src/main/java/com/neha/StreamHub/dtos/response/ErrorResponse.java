package com.neha.StreamHub.dtos.response;

import org.springframework.http.HttpStatus;

public record ErrorResponse(
        String message,
        HttpStatus httpStatus,
        int statusCode
      )
{
}

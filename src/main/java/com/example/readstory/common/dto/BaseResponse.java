package com.example.readstory.common.dto;

import com.example.readstory.common.enums.Status;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;

@Getter
@Builder
public class BaseResponse<T> {
    private Status status;
    private T data;
    @Nullable private String message;

    @Builder.Default
    @Setter(AccessLevel.NONE)
    private ZonedDateTime createdTime = ZonedDateTime.now();
}

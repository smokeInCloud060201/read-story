package com.example.readstory.common.utils;

import com.example.readstory.common.dto.BaseResponse;
import com.example.readstory.common.enums.Status;
import lombok.experimental.UtilityClass;

@UtilityClass
public class BaseResponseMapper {

    public static <T> BaseResponse<T> toSuccess(T data) {
        return BaseResponse.<T>builder()
                .data(data)
                .status(Status.SUCCESS)
                .build();
    }
}

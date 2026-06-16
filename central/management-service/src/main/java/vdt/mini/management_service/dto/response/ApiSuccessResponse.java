package vdt.mini.management_service.dto.response;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

@SuperBuilder
@Setter
@Getter
public class ApiSuccessResponse<T> extends ApiResponse implements Serializable {
    private T data;
}

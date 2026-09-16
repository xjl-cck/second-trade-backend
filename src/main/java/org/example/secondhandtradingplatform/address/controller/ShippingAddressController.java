package org.example.secondhandtradingplatform.address.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.secondhandtradingplatform.address.dto.AddressResponse;
import org.example.secondhandtradingplatform.address.dto.AddressSaveRequest;
import org.example.secondhandtradingplatform.address.service.ShippingAddressService;
import org.example.secondhandtradingplatform.common.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
@Tag(name = "收货地址", description = "当前用户的收货地址管理")
public class ShippingAddressController {

    private final ShippingAddressService shippingAddressService;

    public ShippingAddressController(ShippingAddressService shippingAddressService) {
        this.shippingAddressService = shippingAddressService;
    }

    @GetMapping
    @Operation(summary = "收货地址列表")
    public Result<List<AddressResponse>> list() {
        return Result.success(shippingAddressService.list());
    }

    @PostMapping
    @Operation(summary = "新增收货地址")
    public Result<AddressResponse> create(@Valid @RequestBody AddressSaveRequest request) {
        return Result.success(shippingAddressService.create(request));
    }

    @PutMapping("/{addressId}")
    @Operation(summary = "编辑收货地址")
    public Result<AddressResponse> update(@PathVariable Long addressId,
                                          @Valid @RequestBody AddressSaveRequest request) {
        return Result.success(shippingAddressService.update(addressId, request));
    }

    @PutMapping("/{addressId}/default")
    @Operation(summary = "设为默认收货地址")
    public Result<AddressResponse> setDefault(@PathVariable Long addressId) {
        return Result.success(shippingAddressService.setDefault(addressId));
    }

    @DeleteMapping("/{addressId}")
    @Operation(summary = "删除收货地址")
    public Result<Void> delete(@PathVariable Long addressId) {
        shippingAddressService.delete(addressId);
        return Result.success();
    }
}

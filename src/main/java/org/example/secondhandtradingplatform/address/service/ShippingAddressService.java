package org.example.secondhandtradingplatform.address.service;

import org.example.secondhandtradingplatform.address.dto.AddressResponse;
import org.example.secondhandtradingplatform.address.dto.AddressSaveRequest;
import org.example.secondhandtradingplatform.address.entity.ShippingAddress;

import java.util.List;

public interface ShippingAddressService {

    List<AddressResponse> list();

    AddressResponse create(AddressSaveRequest request);

    AddressResponse update(Long addressId, AddressSaveRequest request);

    void delete(Long addressId);

    AddressResponse setDefault(Long addressId);

    ShippingAddress getForCurrentUser(Long addressId);
}

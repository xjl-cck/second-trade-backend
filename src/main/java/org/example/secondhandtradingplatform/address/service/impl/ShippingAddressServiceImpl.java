package org.example.secondhandtradingplatform.address.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.secondhandtradingplatform.address.dto.AddressResponse;
import org.example.secondhandtradingplatform.address.dto.AddressSaveRequest;
import org.example.secondhandtradingplatform.address.entity.ShippingAddress;
import org.example.secondhandtradingplatform.address.mapper.ShippingAddressMapper;
import org.example.secondhandtradingplatform.address.service.ShippingAddressService;
import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.example.secondhandtradingplatform.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ShippingAddressServiceImpl implements ShippingAddressService {

    private final ShippingAddressMapper shippingAddressMapper;

    public ShippingAddressServiceImpl(ShippingAddressMapper shippingAddressMapper) {
        this.shippingAddressMapper = shippingAddressMapper;
    }

    @Override
    public List<AddressResponse> list() {
        Long userId = requireLoginUserId();
        return shippingAddressMapper.selectList(Wrappers.<ShippingAddress>lambdaQuery()
                        .eq(ShippingAddress::getUserId, userId)
                        .orderByDesc(ShippingAddress::getIsDefault)
                        .orderByDesc(ShippingAddress::getUpdateTime)
                        .orderByDesc(ShippingAddress::getId))
                .stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressResponse create(AddressSaveRequest request) {
        Long userId = requireLoginUserId();
        boolean hasAddress = shippingAddressMapper.exists(Wrappers.<ShippingAddress>lambdaQuery()
                .eq(ShippingAddress::getUserId, userId));
        ShippingAddress address = new ShippingAddress();
        address.setUserId(userId);
        copy(request, address);
        address.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()) || !hasAddress ? 1 : 0);
        address.setIsDeleted(0);
        if (Integer.valueOf(1).equals(address.getIsDefault())) {
            shippingAddressMapper.clearDefaultByUserId(userId);
        }
        shippingAddressMapper.insert(address);
        return AddressResponse.from(address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressResponse update(Long addressId, AddressSaveRequest request) {
        ShippingAddress address = getForCurrentUser(addressId);
        copy(request, address);
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            shippingAddressMapper.clearDefaultByUserId(address.getUserId());
            address.setIsDefault(1);
        }
        shippingAddressMapper.updateById(address);
        return AddressResponse.from(address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long addressId) {
        shippingAddressMapper.deleteById(getForCurrentUser(addressId).getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressResponse setDefault(Long addressId) {
        ShippingAddress address = getForCurrentUser(addressId);
        shippingAddressMapper.clearDefaultByUserId(address.getUserId());
        address.setIsDefault(1);
        shippingAddressMapper.updateById(address);
        return AddressResponse.from(address);
    }

    @Override
    public ShippingAddress getForCurrentUser(Long addressId) {
        Long userId = requireLoginUserId();
        ShippingAddress address = shippingAddressMapper.selectOne(Wrappers.<ShippingAddress>lambdaQuery()
                .eq(ShippingAddress::getId, addressId)
                .eq(ShippingAddress::getUserId, userId));
        if (address == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "收货地址不存在");
        }
        return address;
    }

    private void copy(AddressSaveRequest request, ShippingAddress address) {
        address.setReceiverName(request.getReceiverName().trim());
        address.setReceiverPhone(request.getReceiverPhone().trim());
        address.setProvince(request.getProvince().trim());
        address.setCity(request.getCity().trim());
        address.setDistrict(request.getDistrict().trim());
        address.setDetail(request.getDetail().trim());
    }

    private Long requireLoginUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}

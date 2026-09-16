package org.example.secondhandtradingplatform.address.dto;

import lombok.Data;
import org.example.secondhandtradingplatform.address.entity.ShippingAddress;

@Data
public class AddressResponse {

    private Long id;
    private String receiverName;
    private String receiverPhone;
    private String province;
    private String city;
    private String district;
    private String detail;
    private Integer isDefault;

    public static AddressResponse from(ShippingAddress address) {
        AddressResponse response = new AddressResponse();
        response.setId(address.getId());
        response.setReceiverName(address.getReceiverName());
        response.setReceiverPhone(address.getReceiverPhone());
        response.setProvince(address.getProvince());
        response.setCity(address.getCity());
        response.setDistrict(address.getDistrict());
        response.setDetail(address.getDetail());
        response.setIsDefault(address.getIsDefault());
        return response;
    }
}

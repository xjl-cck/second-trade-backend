package org.example.secondhandtradingplatform.product.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.secondhandtradingplatform.product.dto.ProductCreateRequest;
import org.example.secondhandtradingplatform.product.dto.ProductImageResponse;
import org.example.secondhandtradingplatform.product.dto.ProductQueryRequest;
import org.example.secondhandtradingplatform.product.dto.ProductResponse;
import org.example.secondhandtradingplatform.product.dto.ProductUpdateRequest;
import org.springframework.web.multipart.MultipartFile;

public interface ProductService {

    ProductResponse publish(ProductCreateRequest request);

    ProductResponse update(Long productId, ProductUpdateRequest request);

    void putOnSale(Long productId);

    void takeOffSale(Long productId);

    ProductResponse getDetail(Long productId);

    Page<ProductResponse> page(ProductQueryRequest request);

    ProductImageResponse uploadImage(Long productId, MultipartFile file, Boolean cover, Integer sort);
}

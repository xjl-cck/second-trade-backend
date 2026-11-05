package org.example.secondhandtradingplatform.product.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.secondhandtradingplatform.product.entity.Product;
import org.example.secondhandtradingplatform.product.mapper.ProductMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;
import java.util.List;

@Component
public class ProductSearchIndex {

    private final ElasticsearchClient client;
    private final String index;
    private final ProductMapper productMapper;

    public ProductSearchIndex(ObjectProvider<ElasticsearchClient> clientProvider,
                              @Value("${app.search.elasticsearch.index:products}") String index,
                              ProductMapper productMapper) {
        this.client = clientProvider.getIfAvailable();
        this.index = index;
        this.productMapper = productMapper;
    }

    public boolean isAvailable() {
        return client != null;
    }

    public boolean index(Product product) {
        if (product == null) return false;
        return index(product.getId(), product.getTitle(), product.getDescription(), product.getLocation());
    }

    public boolean index(Long productId, String title, String description, String location) {
        if (!isAvailable() || productId == null) return false;
        try {
            client.index(request -> request.index(index).id(productId.toString()).document(Map.of(
                    "title", title == null ? "" : title,
                    "description", description == null ? "" : description,
                    "location", location == null ? "" : location
            )));
            return true;
        } catch (IOException | RuntimeException ignored) {
            // Search falls back to MySQL while Elasticsearch is unavailable.
            return false;
        }
    }

    /**
     * 删除商品文档。文档本来就不存在（404）也算成功，方便定时任务无脑重试。
     */
    public boolean delete(Long productId) {
        if (!isAvailable() || productId == null) return false;
        try {
            client.delete(request -> request.index(index).id(productId.toString()));
            return true;
        } catch (ElasticsearchException exception) {
            if (exception.status() == 404) {
                return true;
            }
            return false;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    public List<Long> search(String keyword) {
        if (!isAvailable() || !StringUtils.hasText(keyword)) return null;
        try {
            Query query = Query.of(q -> q.multiMatch(m -> m.query(keyword.trim())
                    .fields("title^3", "description", "location")
                    .fuzziness("AUTO")));
            SearchResponse<Product> response = client.search(request -> request
                            .index(index)
                            .size(10000)
                            .query(query), Product.class);
            return response.hits().hits().stream()
                    .map(Hit::id)
                    .map(Long::valueOf)
                    .toList();
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildFromDatabase() {
        if (!isAvailable()) return;
        for (long pageNo = 1; ; pageNo++) {
            List<Product> products = productMapper.selectPage(Page.of(pageNo, 500), null).getRecords();
            if (products.isEmpty()) return;
            products.forEach(this::index);
            if (products.size() < 500) return;
        }
    }
}

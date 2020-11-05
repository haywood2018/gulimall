package com.wxx.gulimall.search.service.impl;

import com.wxx.gulimall.search.config.GulimallElasticSearchConfig;
import com.wxx.gulimall.search.constant.EsConstant;
import com.wxx.gulimall.search.service.MallSearchService;
import com.wxx.gulimall.search.vo.SearchParam;
import com.wxx.gulimall.search.vo.SearchResult;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * @author 她爱微笑
 * @date 2020/11/3
 */
@Service
public class MallSearchServiceImpl implements MallSearchService {

    @Resource
    private RestHighLevelClient client;


    @Override
    public SearchResult search(SearchParam searchParam) {

        SearchResult result = null;

        // 1.准备检索请求
        SearchRequest searchRequest = builderSearchRequest(searchParam);


        try {

            // 2.执行检索请求
            SearchResponse searchResponse = client.search(searchRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);


            // 3.分析响应数据封装成我们需要的格式
            result = builderSearchResult(searchResponse);

        } catch (IOException e) {
            e.printStackTrace();
        }


        return result;
    }

    /**
     * 下划线
     */
    private static final String UNDER_LINE = "_";

    /**
     * 冒号
     */
    private static final String COLON = ":";

    /**
     * 斜杠
     */
    private static final String SLASH = "/";

    /**
     * 准备检索请求
     *
     * @return
     */
    private SearchRequest builderSearchRequest(SearchParam searchParam) {

        // 用来构建DSL语句的
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        // 模糊匹配，过滤（按照属性 分类 品牌 价格区间 库存）
        // 1. 构建bool
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        // 1.1 must - 模糊匹配
        if (StringUtils.isNotEmpty(searchParam.getKeyword())) {
            boolQuery.must(QueryBuilders.matchQuery("skuTitle", searchParam.getKeyword()));
        }

        // 1.2.1 filter - catalogId 3级分类id
        if (searchParam.getCatalog3Id() != null) {
            boolQuery.filter(QueryBuilders.termQuery("catalogId", searchParam.getCatalog3Id()));
        }
        // 1.2.2 filter - brandId 品牌id
        if (!CollectionUtils.isEmpty(searchParam.getBrandId())) {
            boolQuery.filter(QueryBuilders.termsQuery("brandId", searchParam.getBrandId()));
        }

        // 1.2.3 filter - attrs 属性
        if (!CollectionUtils.isEmpty(searchParam.getAttrs())) {
            for (String attr : searchParam.getAttrs()) {
                BoolQueryBuilder attrBoolQuery = QueryBuilders.boolQuery();
                String[] s = attr.split(UNDER_LINE);
                // 属性id
                String attrId = s[0];
                // 属性值
                String[] attrValues = s[1].split(COLON);

                attrBoolQuery.must(QueryBuilders.termQuery("attrs.attrId", attrId));
                attrBoolQuery.must(QueryBuilders.termsQuery("attrs.attrValue", attrValues));

                NestedQueryBuilder attrIdQuery = QueryBuilders.nestedQuery("attrs", attrBoolQuery, ScoreMode.None);
                boolQuery.filter(attrIdQuery);
            }
        }

        // 1.2.4 filter - hasStock 是否有库存
        boolQuery.filter(QueryBuilders.termQuery("hasStock", searchParam.getHasStock() == 1));

        // 1.2.5 filter - skuPrice 价格
        if (StringUtils.isNotBlank(searchParam.getSkuPrice())) {
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("skuPrice");
            /**
             *  1_500 1到500
             *  _500 小于500
             *  500_ 大于500
             */
            String[] split = searchParam.getSkuPrice().split(UNDER_LINE);
            if (split.length == 2) {
                rangeQuery.gte(split[0]).lte(split[1]);
            } else if (split.length == 1) {
                if (searchParam.getSkuPrice().startsWith(UNDER_LINE)) {
                    rangeQuery.lte(split[0]);
                } else {
                    rangeQuery.gte(split[0]);
                }
            }

            boolQuery.filter(rangeQuery);
        }

        sourceBuilder.query(boolQuery);

        // 2.排序 分页 高亮
        // 2.1 排序
        if (StringUtils.isNotBlank(searchParam.getSort())) {

            /**
             * 排序字段_asc/desc
             * saleCount_asc/desc
             * skuPrice_asc/desc
             * hotScore_asc/desc
             */
            String sort = searchParam.getSort();
            String[] s = sort.split(UNDER_LINE);
            sourceBuilder.sort(s[0], s[1].equalsIgnoreCase("ASC") ? SortOrder.ASC : SortOrder.DESC);
        }

        // 2.2 分页
        sourceBuilder.from((searchParam.getPageNum() - 1) * EsConstant.PRODUCT_PAGE_SIZE);
        sourceBuilder.size(EsConstant.PRODUCT_PAGE_SIZE);

        // 2.3 高亮
        if (StringUtils.isNotEmpty(searchParam.getKeyword())) {

            HighlightBuilder builder = new HighlightBuilder();
            builder.field("skuTitle");
            builder.preTags("<b style='color:red'>");
            builder.postTags("</b>");
            sourceBuilder.highlighter(builder);
        }

        // 3.聚合分析
        // 3.1 品牌聚合
        TermsAggregationBuilder brand_agg = AggregationBuilders.terms("brand_agg");
        brand_agg.field("brandId").size(20);
        brand_agg.subAggregation(AggregationBuilders.terms("brand_name_agg").field("brandName"));
        brand_agg.subAggregation(AggregationBuilders.terms("brand_img_agg").field("brandImg"));

        sourceBuilder.aggregation(brand_agg);

        // 3.2 分类聚合
        TermsAggregationBuilder catalog_agg = AggregationBuilders.terms("catalog_agg").field("catalogId").size(20);
        catalog_agg.subAggregation(AggregationBuilders.terms("catalog_name_agg").field("catalogName"));

        sourceBuilder.aggregation(catalog_agg);

        // 3.3 属性聚合
        NestedAggregationBuilder attr_agg = AggregationBuilders.nested("attr_agg", "attrs");

        TermsAggregationBuilder attrsAttrId = AggregationBuilders.terms("attr_id").field("attrs.attrId");
        attrsAttrId.subAggregation(AggregationBuilders.terms("attr_name_agg").field("attrs.attrName"));
        attrsAttrId.subAggregation(AggregationBuilders.terms("attr_value_agg").field("attrs.attrValue"));

        attr_agg.subAggregation(attrsAttrId);
        sourceBuilder.aggregation(attr_agg);


        String s = sourceBuilder.toString();
        System.out.println(s);

        SearchRequest searchRequest = new SearchRequest(new String[]{EsConstant.PRODUCT_INDEX}, sourceBuilder);
        return searchRequest;
    }

    /**
     * 解析响应数据
     *
     * @param searchResponse
     * @return
     */
    private SearchResult builderSearchResult(SearchResponse searchResponse) {
        return null;
    }
}
package com.wxx.gulimall.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wxx.common.utils.PageUtils;
import com.wxx.gulimall.product.entity.SkuInfoEntity;

import java.util.Map;

/**
 * sku信息
 *
 * @author wangxin
 * @email 91907@163.com
 * @date 2020-07-12 20:58:47
 */
public interface SkuInfoService extends IService<SkuInfoEntity> {

    PageUtils queryPage(Map<String, Object> params);
}

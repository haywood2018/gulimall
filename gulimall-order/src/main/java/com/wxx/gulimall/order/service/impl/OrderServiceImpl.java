package com.wxx.gulimall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wxx.common.utils.PageUtils;
import com.wxx.common.utils.Query;
import com.wxx.gulimall.order.dao.OrderDao;
import com.wxx.gulimall.order.entity.OrderEntity;
import com.wxx.gulimall.order.service.OrderService;
import com.wxx.gulimall.order.vo.OrderConfirmVO;
import org.springframework.stereotype.Service;

import java.util.Map;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<OrderEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public OrderConfirmVO confirmOrder() {
        OrderConfirmVO vo = new OrderConfirmVO();



        return vo;
    }

}
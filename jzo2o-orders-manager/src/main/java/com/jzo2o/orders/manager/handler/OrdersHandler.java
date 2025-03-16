package com.jzo2o.orders.manager.handler;

import java.util.List;
import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import com.jzo2o.common.constants.UserType;
import com.jzo2o.common.utils.CollUtils;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.manager.model.dto.OrderCancelDTO;
import com.jzo2o.orders.manager.service.IOrdersCreateService;
import com.jzo2o.orders.manager.service.IOrdersManagerService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;

import cn.hutool.core.bean.BeanUtil;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OrdersHandler {

    @Resource
    private IOrdersCreateService ordersCreateService;

    @Resource
    private IOrdersManagerService ordersManagerService;

    /**
     * 支付超时取消订单
     * 每分钟执行一次
     */
    @XxlJob(value = "cancelOverTimePayOrder")
    public void cancelOverTimePayOrder() {
        List<Orders> orders = ordersCreateService.queryOverTimePayOrdersListByCount(100);
        if (CollUtils.isEmpty(orders)) {
            XxlJobHelper.log("查询超时订单列表为空！");
            return;
        }

        for (Orders order : orders) {
            OrderCancelDTO orderCancelDTO = BeanUtil.toBean(order, OrderCancelDTO.class);
            orderCancelDTO.setCurrentUserType(UserType.SYSTEM);
            orderCancelDTO.setCancelReason("订单超时支付，自动取消");
            ordersManagerService.cancel(orderCancelDTO);
        }
    }
}

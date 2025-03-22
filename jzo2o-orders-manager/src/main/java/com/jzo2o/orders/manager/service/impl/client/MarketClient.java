package com.jzo2o.orders.manager.service.impl.client;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.jzo2o.api.market.CouponApi;
import com.jzo2o.api.market.dto.request.CouponUseReqDTO;
import com.jzo2o.api.market.dto.response.AvailableCouponsResDTO;
import com.jzo2o.api.market.dto.response.CouponUseResDTO;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MarketClient {
    @Resource
    private CouponApi couponApi;
    @SentinelResource(value = "getAvailable", fallback = "getAvailableFallback", blockHandler = "getAvailableBlockHandler")
    public List<AvailableCouponsResDTO> getAvailable(BigDecimal totalAmount){
        log.error("查询可用优惠券,订单金额:{}",totalAmount);
        return couponApi.getAvailable(totalAmount);
    }
    @SentinelResource(value = "use", fallback = "getAvailableFallback", blockHandler = "getAvailableBlockHandler")
    public CouponUseResDTO use(CouponUseReqDTO couponUseReqDTO){
        log.error("核销优惠券:{}",couponUseReqDTO.toString());
        return couponApi.use(couponUseReqDTO);
    }
    //执行异常走
    public List<AvailableCouponsResDTO> getAvailableFallback(BigDecimal totalAmount, Throwable throwable) {
        log.error("非限流、熔断等导致的异常执行的降级方法，totalAmount:{},throwable:", totalAmount, throwable);
        return Collections.emptyList();
    }

    //熔断后的降级逻辑
    public List<AvailableCouponsResDTO> getAvailableBlockHandler(BigDecimal totalAmount, BlockException blockException) {
        log.error("触发限流、熔断时执行的降级方法，totalAmount:{},blockException:", totalAmount, blockException);
        return Collections.emptyList();
    }
}

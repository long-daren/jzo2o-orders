package com.jzo2o.orders.manager.service.impl.client;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.jzo2o.api.foundations.ServeApi;
import com.jzo2o.api.foundations.dto.response.ServeAggregationResDTO;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FoundationClient {
    @Resource
    private ServeApi serveApi;

    @SentinelResource(value = "getServeDetail",fallback = "detailFallback",blockHandler = "detailBlockHandler")
    public ServeAggregationResDTO findById(Long id) {
        log.error("根据id查询服务信息，id:{}", id);
        ServeAggregationResDTO serveAggregationResDTO = serveApi.findById(id);
        return serveAggregationResDTO;
    }

    public ServeAggregationResDTO detailFallback(Long id, Throwable throwable) {
        log.error("非限流、熔断等导致的异常执行的降级方法，id:{},throwable:", id, throwable);
        return null;
    }

    public ServeAggregationResDTO detailBlockHandler(Long id, BlockException blockException) {
        log.error("触发限流、熔断时执行的降级方法，id:{},blockException:", id, blockException);
        return null;
    }


}

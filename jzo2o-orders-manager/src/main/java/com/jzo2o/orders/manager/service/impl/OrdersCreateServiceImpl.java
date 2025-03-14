package com.jzo2o.orders.manager.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.annotation.Resource;
import javax.swing.plaf.metal.MetalBorders;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.customer.dto.response.AddressBookResDTO;
import com.jzo2o.api.foundations.dto.response.ServeAggregationResDTO;
import com.jzo2o.common.expcetions.CommonException;
import com.jzo2o.common.utils.DateUtils;
import com.jzo2o.common.utils.NumberUtils;
import com.jzo2o.mvc.utils.UserContext;
import com.jzo2o.orders.base.enums.OrderPayStatusEnum;
import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.mapper.OrdersMapper;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.manager.model.dto.request.PlaceOrderReqDTO;
import com.jzo2o.orders.manager.model.dto.response.PlaceOrderResDTO;
import com.jzo2o.orders.manager.service.IOrdersCreateService;
import com.jzo2o.orders.manager.service.impl.client.CustomerClient;
import com.jzo2o.orders.manager.service.impl.client.FoundationClient;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.jzo2o.orders.base.constants.RedisConstants.Lock.ORDERS_SHARD_KEY_ID_GENERATOR;

/**
 * <p>
 * 下单服务类
 * </p>
 *
 * @author itcast
 * @since 2023-07-10
 */
@Slf4j
@Service
public class OrdersCreateServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements IOrdersCreateService {


    @Resource
    private CustomerClient customerClient;

    @Resource
    private FoundationClient foundationClient;

    @Resource
    private RedisTemplate<String,Long> redisTemplate;

    @Resource
    private OrdersCreateServiceImpl owner;
    /**
     * 下单接口
     *
     * @param placeOrderReqDTO
     * @return
     */
    @Override
    public PlaceOrderResDTO placeOrder(PlaceOrderReqDTO placeOrderReqDTO) {

        Orders orders = new Orders();
        //地址簿id
        Long addressBookId = placeOrderReqDTO.getAddressBookId();
        //下单人信息，获取地址簿，调用jzo2o-customer服务获取
        AddressBookResDTO detail = customerClient.getDetail(addressBookId);

        //服务相关信息,调用jzo2o-foundations获取
        ServeAggregationResDTO serveAggregationResDTO = foundationClient.findById(placeOrderReqDTO.getServeId());

        //组装订单信息，插入数据库订单表，订单状态为待支付
        //生成订单号
        long orderId = generateOrderId();
        orders.setId(orderId);
        orders.setUserId(UserContext.currentUserId());
        orders.setServeTypeId(serveAggregationResDTO.getServeTypeId());
        orders.setServeTypeName(serveAggregationResDTO.getServeTypeName());
        orders.setServeItemId(serveAggregationResDTO.getServeItemId());
        orders.setServeItemName(serveAggregationResDTO.getServeItemName());
        orders.setServeItemImg(serveAggregationResDTO.getServeItemImg());
        orders.setUnit(serveAggregationResDTO.getUnit());
        orders.setServeId(placeOrderReqDTO.getServeId());
        orders.setOrdersStatus(OrderStatusEnum.NO_PAY.getStatus());
        orders.setPayStatus(OrderPayStatusEnum.NO_PAY.getStatus());
        //计算价格
        orders.setPrice(serveAggregationResDTO.getPrice());
        orders.setPurNum(placeOrderReqDTO.getPurNum());
        orders.setTotalAmount(serveAggregationResDTO.getPrice().multiply(new BigDecimal(placeOrderReqDTO.getPurNum())));
        orders.setDiscountAmount(new BigDecimal(0));
        orders.setRealPayAmount(NumberUtils.sub(orders.getTotalAmount() , orders.getDiscountAmount()));
        orders.setCityCode(serveAggregationResDTO.getCityCode());
        String serveAddress = new StringBuffer().append(detail.getProvince()).append(detail.getCity())
            .append(detail.getCounty()).append(detail.getAddress()).toString();
        orders.setServeAddress(serveAddress);
        orders.setContactsPhone(detail.getPhone());
        orders.setContactsName(detail.getName());
        orders.setServeStartTime(placeOrderReqDTO.getServeStartTime());
        orders.setLon(detail.getLon());
        orders.setLat(detail.getLat());
        long sort = DateUtils.toEpochMilli(orders.getServeStartTime()) + orders.getId() % 10000;
        orders.setSortBy(sort);
        owner.add(orders);
        PlaceOrderResDTO placeOrderResDTO = new PlaceOrderResDTO();
        placeOrderResDTO.setId(orders.getId());
        return placeOrderResDTO;
    }

    private long generateOrderId() {
        Long increment = redisTemplate.opsForValue().increment(ORDERS_SHARD_KEY_ID_GENERATOR, 1);
        Long orderId = DateUtils.getFormatDate(LocalDateTime.now(), "yyMMdd") * 10000000000000L + increment;
        return orderId;
    }

    @Transactional(rollbackFor = Exception.class)
    public void add(Orders orders){
        boolean save = save(orders);
        if (!save) {
            throw new CommonException("下单失败");
        }
    }
}

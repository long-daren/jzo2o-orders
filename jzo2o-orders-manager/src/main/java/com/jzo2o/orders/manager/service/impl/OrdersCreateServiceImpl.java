package com.jzo2o.orders.manager.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.annotation.Resource;
import javax.swing.plaf.metal.MetalBorders;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.customer.dto.response.AddressBookResDTO;
import com.jzo2o.api.foundations.dto.response.ServeAggregationResDTO;
import com.jzo2o.api.trade.NativePayApi;
import com.jzo2o.api.trade.TradingApi;
import com.jzo2o.api.trade.dto.request.NativePayReqDTO;
import com.jzo2o.api.trade.dto.response.NativePayResDTO;
import com.jzo2o.api.trade.dto.response.TradingResDTO;
import com.jzo2o.api.trade.enums.PayChannelEnum;
import com.jzo2o.api.trade.enums.TradingStateEnum;
import com.jzo2o.common.expcetions.CommonException;
import com.jzo2o.common.model.msg.TradeStatusMsg;
import com.jzo2o.common.utils.BeanUtils;
import com.jzo2o.common.utils.DateUtils;
import com.jzo2o.common.utils.NumberUtils;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.mvc.utils.UserContext;
import com.jzo2o.orders.base.enums.OrderPayStatusEnum;
import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.mapper.OrdersMapper;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.manager.model.dto.request.OrdersPayReqDTO;
import com.jzo2o.orders.manager.model.dto.request.PlaceOrderReqDTO;
import com.jzo2o.orders.manager.model.dto.response.OrdersPayResDTO;
import com.jzo2o.orders.manager.model.dto.response.PlaceOrderResDTO;
import com.jzo2o.orders.manager.porperties.TradeProperties;
import com.jzo2o.orders.manager.service.IOrdersCreateService;
import com.jzo2o.orders.manager.service.impl.client.CustomerClient;
import com.jzo2o.orders.manager.service.impl.client.FoundationClient;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.jzo2o.common.constants.ErrorInfo.Code.TRADE_FAILED;
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

    @Resource
    private TradeProperties tradeProperties;
    @Resource
    private NativePayApi nativePayApi;

    @Resource
    private TradingApi tradingApi;
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

    /**
     * 订单支付
     *
     * @param id              订单id
     * @param ordersPayReqDTO 订单支付请求体
     * @return 订单支付响应体
     */
    @Override
    public OrdersPayResDTO pay(Long id, OrdersPayReqDTO ordersPayReqDTO) {
        Orders orders = getById(id);
        if (ObjectUtils.isNull(orders)) {
            throw new CommonException(TRADE_FAILED, "订单不存在");
        }
        if (orders.getPayStatus() == OrderPayStatusEnum.PAY_SUCCESS.getStatus() &&
                ObjectUtils.isNotEmpty(orders.getTradingOrderNo())) {
            OrdersPayResDTO ordersPayResDTO = new OrdersPayResDTO();
            BeanUtil.copyProperties(orders, ordersPayResDTO);
            ordersPayResDTO.setProductOrderNo(orders.getId());
            return ordersPayResDTO;
        } else {
            //生成二维码
            NativePayResDTO nativePayResDTO = generateQrCode(orders, ordersPayReqDTO.getTradingChannel());
            OrdersPayResDTO ordersPayResDTO = BeanUtil.toBean(nativePayResDTO, OrdersPayResDTO.class);
            return ordersPayResDTO;
        }
    }

    /**
     * 请求支付服务查询支付结果
     *
     * @param id 订单id
     * @return 订单支付结果
     */
    @Override
    public OrdersPayResDTO getPayResultFromTradServer(Long id) {
        //查询订单表
        Orders orders = baseMapper.selectById(id);
        if (ObjectUtil.isNull(orders)) {
            throw new CommonException(TRADE_FAILED, "订单不存在");
        }
        //支付结果
        Integer payStatus = orders.getPayStatus();
        //未支付且已存在支付服务的交易单号此时远程调用支付服务查询支付结果
        if (OrderPayStatusEnum.NO_PAY.getStatus() == payStatus
            && ObjectUtil.isNotEmpty(orders.getTradingOrderNo())) {
            //远程调用支付服务查询支付结果
            TradingResDTO tradingResDTO = tradingApi.findTradResultByTradingOrderNo(orders.getTradingOrderNo());
            //如果支付成功这里更新订单状态
            if (ObjectUtil.isNotNull(tradingResDTO)
                && ObjectUtil.equals(tradingResDTO.getTradingState(), TradingStateEnum.YJS)) {
                //设置订单的支付状态成功
                TradeStatusMsg msg = TradeStatusMsg.builder()
                    .productOrderNo(orders.getId())
                    .tradingChannel(tradingResDTO.getTradingChannel())
                    .statusCode(TradingStateEnum.YJS.getCode())
                    .tradingOrderNo(tradingResDTO.getTradingOrderNo())
                    .transactionId(tradingResDTO.getTransactionId())
                    .build();
                owner.paySuccess(msg);
                //构造返回数据
                OrdersPayResDTO ordersPayResDTO = BeanUtils.toBean(msg, OrdersPayResDTO.class);
                ordersPayResDTO.setPayStatus(OrderPayStatusEnum.PAY_SUCCESS.getStatus());
                return ordersPayResDTO;
            }
        }
        OrdersPayResDTO ordersPayResDTO = new OrdersPayResDTO();
        ordersPayResDTO.setPayStatus(payStatus);
        ordersPayResDTO.setProductOrderNo(orders.getId());
        ordersPayResDTO.setTradingOrderNo(orders.getTradingOrderNo());
        ordersPayResDTO.setTradingChannel(orders.getTradingChannel());
        return ordersPayResDTO;
    }

    /**
     * 支付成功， 更新数据库的订单表及其他信息
     *
     * @param tradeStatusMsg 交易状态消息
     */
    @Override
    public void paySuccess(TradeStatusMsg tradeStatusMsg) {
        //查询订单
        Orders orders = baseMapper.selectById(tradeStatusMsg.getProductOrderNo());
        if (ObjectUtil.isNull(orders)) {
            throw new CommonException(TRADE_FAILED, "订单不存在");
        }
        //校验支付状态如果不是待支付状态则不作处理
        if (ObjectUtil.notEqual(OrderPayStatusEnum.NO_PAY.getStatus(), orders.getPayStatus())) {
            log.info("更新订单支付成功，当前订单:{}支付状态不是待支付状态", orders.getId());
            return;
        }
        //校验订单状态如果不是待支付状态则不作处理
        if (ObjectUtils.notEqual(OrderStatusEnum.NO_PAY.getStatus(),orders.getOrdersStatus())) {
            log.info("更新订单支付成功，当前订单:{}状态不是待支付状态", orders.getId());
        }

        //第三方支付单号校验
        if (ObjectUtil.isEmpty(tradeStatusMsg.getTransactionId())) {
            throw new CommonException("支付成功通知缺少第三方支付单号");
        }
        //更新订单的支付状态及第三方交易单号等信息
        boolean update = lambdaUpdate()
            .eq(Orders::getId, orders.getId())
            .set(Orders::getPayTime, LocalDateTime.now())//支付时间
            .set(Orders::getTradingOrderNo, tradeStatusMsg.getTradingOrderNo())//交易单号
            .set(Orders::getTradingChannel, tradeStatusMsg.getTradingChannel())//支付渠道
            .set(Orders::getTransactionId, tradeStatusMsg.getTransactionId())//第三方支付交易号
            .set(Orders::getPayStatus, OrderPayStatusEnum.PAY_SUCCESS.getStatus())//支付状态
            .set(Orders::getOrdersStatus, OrderStatusEnum.DISPATCHING.getStatus())//订单状态更新为派单中
            .update();
        if(!update){
            log.info("更新订单:{}支付成功失败", orders.getId());
            throw new CommonException("更新订单"+orders.getId()+"支付成功失败");
        }

    }

    //生成二维码
    private NativePayResDTO generateQrCode(Orders orders, PayChannelEnum tradingChannel) {

        //判断支付渠道
        Long enterpriseId = ObjectUtil.equal(PayChannelEnum.ALI_PAY, tradingChannel) ?
            tradeProperties.getAliEnterpriseId() : tradeProperties.getWechatEnterpriseId();

        //构建支付请求参数
        NativePayReqDTO nativePayReqDTO = new NativePayReqDTO();
        //商户号
        nativePayReqDTO.setEnterpriseId(enterpriseId);
        //业务系统标识
        nativePayReqDTO.setProductAppId("jzo2o.orders");
        //家政订单号
        nativePayReqDTO.setProductOrderNo(orders.getId());
        //支付渠道
        nativePayReqDTO.setTradingChannel(tradingChannel);
        //支付金额
        nativePayReqDTO.setTradingAmount(orders.getRealPayAmount());
        //备注信息
        nativePayReqDTO.setMemo(orders.getServeItemName());
        //判断是否切换支付渠道
        if (ObjectUtil.isNotEmpty(orders.getTradingChannel())
            && ObjectUtil.notEqual(orders.getTradingChannel(), tradingChannel.toString())) {
            nativePayReqDTO.setChangeChannel(true);
        }
        //生成支付二维码
        NativePayResDTO downLineTrading = nativePayApi.createDownLineTrading(nativePayReqDTO);
        if(ObjectUtils.isNotNull(downLineTrading)){
            log.info("订单:{}请求支付,生成二维码:{}",orders.getId(),downLineTrading.toString());
            //将二维码更新到交易订单中
            boolean update = lambdaUpdate()
                .eq(Orders::getId, downLineTrading.getProductOrderNo())
                .set(Orders::getTradingOrderNo, downLineTrading.getTradingOrderNo())
                .set(Orders::getTradingChannel, downLineTrading.getTradingChannel())
                .update();
            if(!update){
                throw new CommonException("订单:"+orders.getId()+"请求支付更新交易单号失败");
            }
        }
        return downLineTrading;
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

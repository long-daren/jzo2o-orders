package com.jzo2o.orders.manager.model.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("运营端订单查询响应模型")
public class OperationOrdersPageResDTO {
    /**
     * 订单状态，0：待支付，100：派单中，200：待服务，300：服务中，400：待评价，500：订单完成，600：订单取消,700：订单关闭
     */
    @ApiModelProperty("订单状态，0：待支付，100：派单中，200：待服务，300：服务中，400：待评价，500：订单完成，600：订单取消,700：订单关闭")
    private Integer ordersStatus;

    @ApiModelProperty("服务项名称")
    private String serveItemName;

    /**
     * 退款状态，0：发起退款，1：退款中，2：退款成功  3：退款失败
     */
    @ApiModelProperty("退款状态，0：发起退款，1：退款中，2：退款成功  3：退款失败")
    private Integer refundStatus;

    @ApiModelProperty("服务单id")
    private Long serveId;

    @ApiModelProperty("更新时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @ApiModelProperty("订单编码")
    private String ordersCode;

    @ApiModelProperty("服务地址")
    private String serveAddress;

    @ApiModelProperty("联系人姓名")
    private String contactsName;

    @ApiModelProperty("服务项id")
    private Long serveItemId;

    @ApiModelProperty("订单总金额")
    private BigDecimal totalAmount;

    @ApiModelProperty("联系人电话")
    private String contactsPhone;

    @ApiModelProperty("服务结束时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime serveEndTime;

    @ApiModelProperty("创建时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @ApiModelProperty("单价")
    private BigDecimal price;

    @ApiModelProperty("服务项图片")
    private String serveItemImg;

    @ApiModelProperty("实际支付金额")
    private BigDecimal realPayAmount;

    @ApiModelProperty("服务开始时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime serveStartTime;

    @ApiModelProperty("排序字段")
    private Integer sortBy;

    @ApiModelProperty("订单id")
    private Long id;

    @ApiModelProperty("评价状态，0：未评价，1：已评价")
    private Integer evaluateStatus;

    @ApiModelProperty("支付状态，0：待支付，1：支付成功")
    private Integer payStatus;

    @ApiModelProperty("服务数量")
    private Integer purNum;

}

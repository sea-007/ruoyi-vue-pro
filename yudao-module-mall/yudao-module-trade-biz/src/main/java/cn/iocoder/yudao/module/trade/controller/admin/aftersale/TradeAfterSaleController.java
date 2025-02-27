package cn.iocoder.yudao.module.trade.controller.admin.aftersale;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.operatelog.core.annotations.OperateLog;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.pay.api.notify.dto.PayRefundNotifyReqDTO;
import cn.iocoder.yudao.module.trade.controller.admin.aftersale.vo.*;
import cn.iocoder.yudao.module.trade.convert.aftersale.TradeAfterSaleConvert;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.TradeAfterSaleDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.framework.aftersalelog.core.dto.TradeAfterSaleLogRespDTO;
import cn.iocoder.yudao.module.trade.framework.aftersalelog.core.service.AfterSaleLogService;
import cn.iocoder.yudao.module.trade.service.aftersale.TradeAfterSaleService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.framework.common.util.servlet.ServletUtils.getClientIP;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 售后订单")
@RestController
@RequestMapping("/trade/after-sale")
@Validated
@Slf4j
public class TradeAfterSaleController {

    @Resource
    private TradeAfterSaleService afterSaleService;
    @Resource
    private TradeOrderQueryService tradeOrderQueryService;
    @Resource
    private AfterSaleLogService afterSaleLogService;
    @Resource
    private MemberUserApi memberUserApi;

    @GetMapping("/page")
    @Operation(summary = "获得售后订单分页")
    @PreAuthorize("@ss.hasPermission('trade:after-sale:query')")
    public CommonResult<PageResult<TradeAfterSaleRespPageItemVO>> getAfterSalePage(@Valid TradeAfterSalePageReqVO pageVO) {
        // 查询售后
        PageResult<TradeAfterSaleDO> pageResult = afterSaleService.getAfterSalePage(pageVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty());
        }

        // 查询会员
        Map<Long, MemberUserRespDTO> memberUsers = memberUserApi.getUserMap(
                convertSet(pageResult.getList(), TradeAfterSaleDO::getUserId));
        return success(TradeAfterSaleConvert.INSTANCE.convertPage(pageResult, memberUsers));
    }

    @GetMapping("/get-detail")
    @Operation(summary = "获得售后订单详情")
    @Parameter(name = "id", description = "售后编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('trade:after-sale:query')")
    public CommonResult<TradeAfterSaleDetailRespVO> getOrderDetail(@RequestParam("id") Long id) {
        // 查询订单
        TradeAfterSaleDO afterSale = afterSaleService.getAfterSale(id);
        // 查询订单
        TradeOrderDO order = tradeOrderQueryService.getOrder(afterSale.getOrderId());
        // 查询订单项
        List<TradeOrderItemDO> orderItems = tradeOrderQueryService.getOrderItemListByOrderId(id);
        // 拼接数据
        MemberUserRespDTO user = memberUserApi.getUser(afterSale.getUserId());
        // 获取售后日志
        List<TradeAfterSaleLogRespDTO> logs = afterSaleLogService.getLog(afterSale.getId());
        // TODO 方便测试看效果，review 后移除
        if (logs == null) {
            logs = new ArrayList<>();
        }
        for (int i = 1; i <= 6; i++) {
            TradeAfterSaleLogRespDTO respVO = new TradeAfterSaleLogRespDTO();
            respVO.setId((long) i);
            respVO.setUserId((long) i);
            respVO.setUserType(1);
            respVO.setAfterSaleId(id);
            respVO.setOrderId((long) i);
            respVO.setOrderItemId((long) i);
            respVO.setBeforeStatus((i - 1) * 10);
            respVO.setAfterStatus(i * 10);
            respVO.setContent("66+6");
            respVO.setCreateTime(LocalDateTime.now());
            logs.add(respVO);
        }
        return success(TradeAfterSaleConvert.INSTANCE.convert(afterSale, order, orderItems, user, logs));
    }

    @PutMapping("/agree")
    @Operation(summary = "同意售后")
    @Parameter(name = "id", description = "售后编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('trade:after-sale:agree')")
    public CommonResult<Boolean> agreeAfterSale(@RequestParam("id") Long id) {
        afterSaleService.agreeAfterSale(getLoginUserId(), id);
        return success(true);
    }

    @PutMapping("/disagree")
    @Operation(summary = "拒绝售后")
    @PreAuthorize("@ss.hasPermission('trade:after-sale:disagree')")
    public CommonResult<Boolean> disagreeAfterSale(@RequestBody TradeAfterSaleDisagreeReqVO confirmReqVO) {
        afterSaleService.disagreeAfterSale(getLoginUserId(), confirmReqVO);
        return success(true);
    }

    @PutMapping("/receive")
    @Operation(summary = "确认收货")
    @Parameter(name = "id", description = "售后编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('trade:after-sale:receive')")
    public CommonResult<Boolean> receiveAfterSale(@RequestParam("id") Long id) {
        afterSaleService.receiveAfterSale(getLoginUserId(), id);
        return success(true);
    }

    @PutMapping("/refuse")
    @Operation(summary = "拒绝收货")
    @Parameter(name = "id", description = "售后编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('trade:after-sale:receive')")
    public CommonResult<Boolean> refuseAfterSale(TradeAfterSaleRefuseReqVO refuseReqVO) {
        afterSaleService.refuseAfterSale(getLoginUserId(), refuseReqVO);
        return success(true);
    }

    @PutMapping("/refund")
    @Operation(summary = "确认退款")
    @Parameter(name = "id", description = "售后编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('trade:after-sale:refund')")
    public CommonResult<Boolean> refundAfterSale(@RequestParam("id") Long id) {
        afterSaleService.refundAfterSale(getLoginUserId(), getClientIP(), id);
        return success(true);
    }

    @PostMapping("/update-refunded")
    @Operation(summary = "更新售后订单为已退款") // 由 pay-module 支付服务，进行回调，可见 PayNotifyJob
    @PermitAll // 无需登录，安全由 PayDemoOrderService 内部校验实现
    @OperateLog(enable = false) // 禁用操作日志，因为没有操作人
    public CommonResult<Boolean> updateAfterRefund(@RequestBody PayRefundNotifyReqDTO notifyReqDTO) {
        // 目前业务逻辑，不需要做任何事情
        // 当然，退款会有小概率会失败的情况，可以监控失败状态，进行告警
        log.info("[updateAfterRefund][notifyReqDTO({})]", notifyReqDTO);
        return success(true);
    }

}

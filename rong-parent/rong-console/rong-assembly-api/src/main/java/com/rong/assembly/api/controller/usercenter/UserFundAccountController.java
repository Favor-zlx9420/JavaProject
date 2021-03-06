package com.rong.assembly.api.controller.usercenter;

import com.rong.assembly.api.module.request.uc.account.*;
import com.rong.assembly.api.module.response.user.TrUserFundAccountIndex;
import com.rong.assembly.api.service.UserManagerService;
import com.rong.common.consts.CommonEnumContainer;
import com.rong.common.exception.DuplicateDataException;
import com.rong.common.module.KvPair;
import com.rong.common.module.Result;
import com.rong.common.module.TvPageList;
import com.rong.common.util.DateUtil;
import com.rong.common.util.WrapperFactory;
import com.rong.tong.fund.mapper.FundNavMapper;
import com.rong.tong.fund.module.entity.TbFundNav;
import com.rong.tong.fund.module.query.TsFundNav;
import com.rong.tong.pfunds.mapper.PfundNavMapper;
import com.rong.tong.pfunds.module.entity.TbMdSecurity;
import com.rong.tong.pfunds.module.entity.TbPfundNav;
import com.rong.tong.pfunds.module.query.TsPfundNav;
import com.rong.user.module.entity.TbUserFundAccount;
import com.rong.user.module.query.TsUserFundAccount;
import com.rong.user.module.request.TqUserFundAccount;
import com.rong.user.module.view.TvUserFundAccount;
import com.rong.user.service.UserFundAccountService;
import com.vitily.mybatis.core.enums.Order;
import com.vitily.mybatis.core.wrapper.PageInfo;
import com.vitily.mybatis.core.wrapper.query.MultiTableQueryWrapper;
import com.vitily.mybatis.core.wrapper.sort.OrderBy;
import com.vitily.mybatis.core.wrapper.update.UpdateWrapper;
import com.vitily.mybatis.util.ClassAssociateTableInfo;
import com.vitily.mybatis.util.CollectionUtils;
import com.vitily.mybatis.util.CompareAlias;
import com.vitily.mybatis.util.Elements;
import com.vitily.mybatis.util.SelectAlias;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

/**
 * ??????????????????
 */
@Api(tags = "??????????????????")
@RestController
@RequestMapping("user/fundAccount")
public class UserFundAccountController {

    @Value("${maxFundAccountOfDay:10}")
    private int maxFundAccountOfDay;
    @Autowired
    private UserFundAccountService userFundAccountService;
    @Autowired
    private PfundNavMapper pfundNavMapper;
    @Autowired
    private FundNavMapper fundNavMapper;
    @Autowired
    private UserManagerService userManagerService;

    /**
     * ????????????????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "????????????????????????")
    @GetMapping(value="index")
    public Result<TrUserFundAccountIndex> index(TqFundAccountIndex req){
        TqFundAccountList reqList = new TqFundAccountList();
        BeanUtils.copyProperties(req,reqList);
        reqList.setPageInfo(new PageInfo());
        Result<TvPageList<TvUserFundAccount>> result = list(reqList);
        TrUserFundAccountIndex fundAccountIndex = userManagerService.selectSumFundIndex(req.getUserId());
        fundAccountIndex.setPageList(result.getContent());
        return Result.success(fundAccountIndex);
    }
    /**
     * ??????????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "??????????????????")
    @GetMapping(value="list")
    public Result<TvPageList<TvUserFundAccount>> list(TqFundAccountList req){
        MultiTableQueryWrapper queryWrapper =
                userFundAccountService.getMultiCommonWrapper()
                        .selectAllFiels(true)
                        .select("md.secShortName,md.secFullName")
                        .select0(
                                //????????????
                                SelectAlias.valueOf("(select nav from `tong-rong`.pfund_nav where security_id=e.security_id order by end_date desc limit 1) priNav",true)
                                //??????????????????
                                ,SelectAlias.valueOf("(select end_date from `tong-rong`.pfund_nav where security_id=e.security_id order by end_date desc limit 1) navDate",true)
                                //????????????
                                ,SelectAlias.valueOf("(select nav from `tong-rong`.fund_nav where security_id=e.security_id order by end_date desc limit 1) raisedNav",true)
                                //??????????????????
                                ,SelectAlias.valueOf("(select end_date from `tong-rong`.fund_nav where security_id=e.security_id order by end_date desc limit 1) navDate",true)
                        )
                        .leftJoin(ClassAssociateTableInfo.valueOf(TbMdSecurity.class,"md"), md->
                                md.eqc(CompareAlias.valueOf("md.securityId"),CompareAlias.valueOf("e.securityId")))
                        .eq(CompareAlias.valueOf("e.userId"),req.getUserId())
                        .orderBy(OrderBy.valueOf(Order.DESC,SelectAlias.valueOf("e.updateDate")))
                        .orderBy(OrderBy.valueOf(Order.DESC,SelectAlias.valueOf("e.createDate")))
                        .page(req.getPageInfo())
                ;
        if(null == req.getDeltag()){
            req.setDeltag(false);
        }
        queryWrapper.eq(CompareAlias.valueOf("e.deltag"),req.getDeltag());
        if(null != req.getSecFullName()){
            queryWrapper.or(x->x
                    .like(CompareAlias.valueOf("md.secShortName"),"%"+req.getSecFullName()+"%")
                    .like(CompareAlias.valueOf("md.secFullName"),"%"+req.getSecFullName()+"%")
            );
        }
        if(null != req.getBeginBuyDate()){
            queryWrapper.ge(CompareAlias.valueOf("e.buyDate"),req.getBeginBuyDate());
        }
        if(null != req.getEndBuyDate()){
            queryWrapper.le(CompareAlias.valueOf("e.buyDate"),req.getEndBuyDate());
        }
        if(null != req.getBeginShare()){
            queryWrapper.ge(CompareAlias.valueOf("e.share"),req.getBeginShare());
        }
        if(null != req.getEndBuyShare()){
            queryWrapper.le(CompareAlias.valueOf("e.share"),req.getEndBuyShare());
        }
        if(null != req.getBeginPrincipal()){
            queryWrapper.ge(CompareAlias.valueOf("e.principal"),req.getBeginPrincipal());
        }
        if(null != req.getEndBuyShare()){
            queryWrapper.le(CompareAlias.valueOf("e.principal"),req.getEndBuyShare());
        }
        if(null != req.getId()){
            queryWrapper.eq(CompareAlias.valueOf("e.id"),req.getId());
        }
        return Result.success(userFundAccountService.selectPageList(queryWrapper));
    }
    /**
     * ??????????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "??????????????????")
    @PostMapping(value="add")
    public Result<Boolean> add(@RequestBody TqAddFundAccount req){
        //

        if(userFundAccountService.selectCount(WrapperFactory.queryWrapper()
                .eq(TsUserFundAccount.Fields.userId,req.getUserId())
                .ge(TsUserFundAccount.Fields.createDate, DateUtil.getCurDateTime(DateUtil.yyyy_MM_dd_EN))
        ) > maxFundAccountOfDay){
            throw new DuplicateDataException("???????????????????????????????????????????????????????????????");
        }
        TbUserFundAccount fundAccount = new TbUserFundAccount();
        BeanUtils.copyProperties(req,fundAccount);
        //???????????????????????????
        if(null == req.getBuyDateStr()){
            fundAccount.setBuyDate(new Date());
        }else{
            fundAccount.setBuyDate(DateUtil.getDate(req.getBuyDateStr(), DateUtil.yyyy_MM_dd_EN));
        }
        //???????????????????????????
        KvPair<Date,BigDecimal> theNavInfo = findNavBySecurityIdAndEndDate(fundAccount.getSecurityId(), fundAccount.getBuyDate());
        if(null == theNavInfo || null == theNavInfo.getValue()){
            return Result.miss(CommonEnumContainer.ResultStatus.QUERY_DOES_NOT_EXIST,"????????????????????????????????????");
        }
        fundAccount.setThenNav(theNavInfo.getValue());
        //?????? = ??????/????????????
        fundAccount.setShare(fundAccount.getPrincipal().divide(fundAccount.getThenNav(),8, RoundingMode.HALF_UP));
        fundAccount.setUpdateDate(new Date());
        userFundAccountService.insert(new TqUserFundAccount().setEntity(fundAccount));
        return Result.success(true);
    }

    /**
     * ??????????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "??????????????????")
    @PostMapping(value="edit")
    public Result<Boolean> edit(@RequestBody TqEditFundAccount req){
        //????????????
        TbUserFundAccount fundAccount = userFundAccountService.selectOne(
                WrapperFactory.queryWrapper()
                        .eq(TsUserFundAccount.Fields.id,req.getId())
                        .eq(TsUserFundAccount.Fields.userId,req.getUserId())
        );
        if(null == fundAccount){
            return Result.miss(CommonEnumContainer.ResultStatus.QUERY_DOES_NOT_EXIST,"?????????????????????");
        }
        fundAccount.setPrincipal(req.getPrincipal());
        //???????????????????????????
        if(null != req.getBuyDateStr()){
            fundAccount.setBuyDate(DateUtil.getDate(req.getBuyDateStr(), DateUtil.yyyy_MM_dd_EN));
            KvPair<Date,BigDecimal> theNavInfo = findNavBySecurityIdAndEndDate(fundAccount.getSecurityId(), fundAccount.getBuyDate());
            if(null == theNavInfo || null == theNavInfo.getValue()){
                return Result.miss(CommonEnumContainer.ResultStatus.QUERY_DOES_NOT_EXIST,"????????????????????????????????????");
            }
            fundAccount.setThenNav(theNavInfo.getValue());
        }
        //??????????????????
        //?????? = ??????/????????????
        fundAccount.setShare(fundAccount.getPrincipal().divide(fundAccount.getThenNav(), 8, RoundingMode.HALF_UP));
        userFundAccountService.updateByPrimary(new TqUserFundAccount().setEntity(fundAccount));
        return Result.success(true);
    }
    private KvPair<Date,BigDecimal> findNavBySecurityIdAndEndDate(Long securityId, Date endDate){
        KvPair<Date,BigDecimal> navInfo = new KvPair<>();
        if(null == endDate){
            return null;
        }
        TbPfundNav pfundNav = pfundNavMapper.selectOne(
                WrapperFactory.queryWrapper().select(TsPfundNav.Fields.nav)
                        .eq(TsPfundNav.Fields.securityId,securityId)
                        .le(TsPfundNav.Fields.endDate,endDate)
                        .orderBy(OrderBy.valueOf(Order.DESC, SelectAlias.valueOf("endDate")))
        );
        if(null != pfundNav){
            navInfo.setKey(pfundNav.getEndDate());
            navInfo.setValue(pfundNav.getNav());
            return navInfo;
        }
        TbFundNav fundNav = fundNavMapper.selectOne(
                WrapperFactory.queryWrapper().select(TsFundNav.Fields.nav)
                        .eq(TsFundNav.Fields.securityId,securityId)
                        .le(TsFundNav.Fields.endDate,endDate)
                        .orderBy(OrderBy.valueOf(Order.DESC, SelectAlias.valueOf("endDate")))
        );
        if(null != fundNav){
            navInfo.setKey(fundNav.getEndDate());
            navInfo.setValue(fundNav.getNav());
            return navInfo;
        }
        return null;
    }

    /**
     * ????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "????????????")
    @PostMapping(value="remove")
    public Result<Boolean> remove(@RequestBody TqDelOrRecFundAccounts req){
        if(CollectionUtils.isEmpty(req.getIds())){
            return Result.miss(CommonEnumContainer.ResultStatus.PARAMETER_IS_NOT_COMPLETE,"ids??????");
        }
        userFundAccountService.updateSelectItem(new UpdateWrapper()
                .update(
                        Elements.valueOf(TsUserFundAccount.Fields.deltag,true)
                        ,
                        Elements.valueOf(TsUserFundAccount.Fields.updateDate,new Date())
                )
                .in(TsUserFundAccount.Fields.id,req.getIds())
                .eq(TsUserFundAccount.Fields.userId,req.getUserId())
        );
        return Result.success(true);
    }

    /**
     * ????????????????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "????????????????????????")
    @PostMapping(value="restore")
    public Result<Boolean> restore(@RequestBody TqDelOrRecFundAccounts req){
        if(CollectionUtils.isEmpty(req.getIds())){
            return Result.miss(CommonEnumContainer.ResultStatus.PARAMETER_IS_NOT_COMPLETE,"ids??????");
        }
        userFundAccountService.updateSelectItem(new UpdateWrapper()
                .update(
                        Elements.valueOf(TsUserFundAccount.Fields.deltag,false)
                        ,
                        Elements.valueOf(TsUserFundAccount.Fields.updateDate,new Date())
                )
                .in(TsUserFundAccount.Fields.id,req.getIds())
                .eq(TsUserFundAccount.Fields.userId,req.getUserId())
        );
        return Result.success(true);
    }

}

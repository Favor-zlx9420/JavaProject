package com.rong.assembly.api.controller.usercenter;

import com.rong.assembly.api.module.request.uc.store.*;
import com.rong.assembly.api.module.response.TrStoreCenterIndex;
import com.rong.common.consts.CommonEnumContainer;
import com.rong.common.module.Result;
import com.rong.common.module.TvPageList;
import com.rong.common.util.CommonUtil;
import com.rong.common.util.EnumHelperUtil;
import com.rong.common.util.StringUtil;
import com.rong.common.util.WrapperFactory;
import com.rong.member.consts.MemEnumContainer;
import com.rong.member.module.entity.TbMemBase;
import com.rong.member.module.query.TsMemBase;
import com.rong.member.service.MemBaseService;
import com.rong.store.module.entity.TbDirectStoreDesign;
import com.rong.store.module.entity.TbDirectStoreOrg;
import com.rong.store.module.entity.TbDirectStoreServiceHistory;
import com.rong.store.module.entity.TbDirectStoreUser;
import com.rong.store.module.query.TsDirectStoreDesign;
import com.rong.store.module.query.TsDirectStoreOrg;
import com.rong.store.module.query.TsDirectStoreServiceHistory;
import com.rong.store.module.query.TsDirectStoreUser;
import com.rong.store.module.request.TqDirectStoreDesign;
import com.rong.store.module.request.TqDirectStoreServiceHistory;
import com.rong.store.module.request.TqDirectStoreUser;
import com.rong.store.module.view.TvDirectStoreDesign;
import com.rong.store.module.view.TvDirectStoreOrg;
import com.rong.store.module.view.TvDirectStoreServiceHistory;
import com.rong.store.module.view.TvDirectStoreUser;
import com.rong.store.service.DirectStoreDesignService;
import com.rong.store.service.DirectStoreOrgService;
import com.rong.store.service.DirectStoreServiceHistoryService;
import com.rong.store.service.DirectStoreUserService;
import com.vitily.mybatis.core.consts.ConstValue;
import com.vitily.mybatis.core.enums.Order;
import com.vitily.mybatis.core.wrapper.PageInfo;
import com.vitily.mybatis.core.wrapper.query.IdWrapper;
import com.vitily.mybatis.core.wrapper.query.MultiTableQueryWrapper;
import com.vitily.mybatis.core.wrapper.query.QueryWrapper;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

@Api(tags = "?????????????????????")
@RestController
@RequestMapping("user/store")
public class StoreCenterController {
    @Autowired
    private DirectStoreOrgService directStoreOrgService;
    @Autowired
    private DirectStoreUserService directStoreUserService;

    @Autowired
    private DirectStoreServiceHistoryService directStoreServiceHistoryService;
    @Autowired
    private MemBaseService memBaseService;
    @Autowired
    private DirectStoreDesignService directStoreDesignService;


    //?????????????????????
    @ApiOperation(value = "?????????????????????")
    @GetMapping("manage/index")
    public Result<TrStoreCenterIndex> index(TqStoreCenterIndex req){
        TvDirectStoreOrg org = directStoreOrgService.selectOneView(directStoreOrgService.getMultiCommonWrapper()
            .eq(CompareAlias.valueOf(TsDirectStoreOrg.Fields.userId),req.getUserId())
        );
        if(null == org){
            return Result.miss(CommonEnumContainer.ResultStatus.QUERY_DOES_NOT_EXIST,"?????????????????????");
        }
        TrStoreCenterIndex info = new TrStoreCenterIndex();
        info.setOrgInfo(org);
        List<TvDirectStoreUser> storeUsers = directStoreUserService.selectViewList(
                WrapperFactory.multiQueryWrapper()
                .selectAllFiels(true)
                .select("m.realName realName,m.lastLoginDate")
                .select0(
                        SelectAlias.valueOf("(select count(1)from tb_direct_store_service_history where customer_user_id=e.user_id and audit_result in(0,1)) serviceNumCount",true)
                        ,
                        SelectAlias.valueOf("(select count(DISTINCT investor_user_id) from tb_direct_store_service_history where customer_user_id=e.user_id and audit_result in(0,1)) serviceUserCount",true)
                )
                .leftJoin(ClassAssociateTableInfo.valueOf(TbMemBase.class,"m"),
                        x->x.eqc(
                                CompareAlias.valueOf(TsMemBase.Fields.id,"m"),
                                CompareAlias.valueOf(TsDirectStoreUser.Fields.userId, ConstValue.MAIN_ALIAS)))
                .eq(CompareAlias.valueOf(TsDirectStoreUser.Fields.partyId),org.getPartyId())
                .eq(CompareAlias.valueOf("e.type"), CommonEnumContainer.StoreUserType.SERVICE.getValue())
                .eq(CompareAlias.valueOf("e.deltag"), CommonEnumContainer.Deltag.NORMAL.getValue())
        );
        org.setCustomerUserCount(storeUsers.size());//??????????????????
        for(TvDirectStoreUser u:storeUsers){
            org.setServiceNumCount(org.getServiceNumCount() + u.getServiceNumCount());
            org.setServiceUserCount(org.getServiceUserCount() + u.getServiceUserCount());
        }
        info.setCustomerUsers(storeUsers);
        return Result.success(info);
    }



    /**
     * 1???????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "????????????")
    @PostMapping("/manage/addCustomer")
    public Result<Boolean> add(@RequestBody TqStoreAlterCustomer req)throws Exception{
        TvDirectStoreOrg org = directStoreOrgService.selectOneView(directStoreOrgService.getMultiCommonWrapper()
                .eq(CompareAlias.valueOf(TsDirectStoreOrg.Fields.userId),req.getUserId())
        );
        if(null == org){
            return Result.miss(CommonEnumContainer.ResultStatus.QUERY_DOES_NOT_EXIST,"?????????????????????");
        }
        if(org.getCustomerUserCount() >=3){
            return Result.miss(CommonEnumContainer.ResultStatus.REPEATING_DATA,"???????????????3??????????????????????????????????????????????????????????????????");
        }
        TbDirectStoreUser has = directStoreUserService.selectOne(new QueryWrapper()
            .eq(TsDirectStoreUser.Fields.userId,req.getCustomerUserId())
        );
        TbMemBase user = memBaseService.selectItemByPrimaryKey(IdWrapper.valueOf(req.getCustomerUserId(), TsMemBase.Fields.type));
        if(null == user || !CommonUtil.isEqual(MemEnumContainer.MemType.???????????????.getValue(),user.getType())){
            return Result.miss(CommonEnumContainer.ResultStatus.QUERY_DOES_NOT_EXIST,"????????????????????????");
        }
        if(null != has){
            return Result.miss(CommonEnumContainer.ResultStatus.REPEATING_DATA,"???????????????????????????????????????????????????????????????");
        }
        has = new TbDirectStoreUser();
        BeanUtils.copyProperties(req,has);
        has.setUserId(req.getCustomerUserId());
        has.setVisible(CommonEnumContainer.YesOrNo.DENY.getValue());
        has.setRecommend(CommonEnumContainer.YesOrNo.DENY.getValue());
        has.setState(CommonEnumContainer.CustomerAuditState.TO_AUDIT.getValue());
        has.setType(CommonEnumContainer.StoreUserType.SERVICE.getValue());
        has.setPartyId(org.getPartyId());
        directStoreUserService.insert(new TqDirectStoreUser().setEntity(has));

        return Result.success(Boolean.TRUE);
    }

    /**
     * 1???????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "????????????")
    @PostMapping("/manage/editCustomer")
    public Result<Boolean> edit(@RequestBody TqStoreAlterCustomer req)throws Exception{
        TbDirectStoreUser storeUser = directStoreUserService.selectOne(
                new QueryWrapper()
                .select("partyId,id")
                .eq(TsDirectStoreUser.Fields.userId,req.getCustomerUserId())
        );
        TbDirectStoreOrg org = directStoreOrgService.selectOne(
                new QueryWrapper()
                        .select("partyId")
                        .eq(TsDirectStoreUser.Fields.userId,req.getUserId())
        );
        if(null == storeUser || null == org || !CommonUtil.isEqual(org.getPartyId(),storeUser.getPartyId())){
            return Result.miss(CommonEnumContainer.ResultStatus.QUERY_DOES_NOT_EXIST,"????????????id??????????????????");
        }

        TbDirectStoreUser up = new TbDirectStoreUser();
        BeanUtils.copyProperties(req,up);
        up.setUserId(null);
        up.setState(CommonEnumContainer.CustomerAuditState.TO_SUBMIT_AGAIN.getValue());
        up.setId(storeUser.getId());
        up.setVisible(CommonEnumContainer.YesOrNo.DENY.getValue());
        directStoreUserService.updateByPrimary(new TqDirectStoreUser().setEntity(up));

        return Result.success(Boolean.TRUE);
    }

    /**
     * 1???????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "????????????")
    @PostMapping("/manage/removeCustomer")
    public Result<Boolean> upload(@RequestBody TqStoreRemoveCustomer req)throws Exception{
        TbDirectStoreUser storeUser = directStoreUserService.selectOne(
                new QueryWrapper()
                        .select("partyId,id")
                        .eq(TsDirectStoreUser.Fields.userId,req.getCustomerUserId())
        );
        TbDirectStoreOrg org = directStoreOrgService.selectOne(
                new QueryWrapper()
                        .select("partyId")
                        .eq(TsDirectStoreUser.Fields.userId,req.getUserId())
        );
        if(null == storeUser || null == org || !CommonUtil.isEqual(org.getPartyId(),storeUser.getPartyId())){
            return Result.miss(CommonEnumContainer.ResultStatus.QUERY_DOES_NOT_EXIST,"????????????id??????????????????");
        }
        storeUser.setDeltag(CommonEnumContainer.Deltag.DELETED.getValue());
        //directStoreUserService.updateByPrimary(new TqDirectStoreUser().setEntity(storeUser));
        directStoreUserService.deleteByPrimaryKey(IdWrapper.valueOf(storeUser.getId()));
        return Result.success(Boolean.TRUE);
    }

    /**
     * ?????????????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "?????????????????????")
    @GetMapping("/manage/service/list")
    public Result<TvPageList<TvDirectStoreServiceHistory>> myServiceList(TqStoreServiceInfoList req){
        TbDirectStoreOrg org = directStoreOrgService.selectOne(
                new QueryWrapper()
                        .select("partyId")
                        .eq(TsDirectStoreUser.Fields.userId,req.getUserId())
        );
        if(null == org){
            return Result.miss(CommonEnumContainer.ResultStatus.QUERY_DOES_NOT_EXIST,"??????????????????");
        }
        if(null == req.getPageInfo()){
            req.setPageInfo(new PageInfo());
        }
        MultiTableQueryWrapper queryWrapper = directStoreServiceHistoryService.getMultiCommonWrapper()
                .eq(CompareAlias.valueOf(TsDirectStoreServiceHistory.Fields.partyId,"e"),org.getPartyId())
                .page(req.getPageInfo())
                ;
        if(null != req.getCustomerUserId()){
            queryWrapper.eq(CompareAlias.valueOf(TsDirectStoreServiceHistory.Fields.customerUserId,"e"),req.getCustomerUserId());
        }
        if(null != req.getAuditResult()){
            queryWrapper.eq(CompareAlias.valueOf(TsDirectStoreServiceHistory.Fields.auditResult,"e"),req.getAuditResult());
        }
        TvPageList<TvDirectStoreServiceHistory> pageList = directStoreServiceHistoryService.selectPageList(queryWrapper);
        for(TvDirectStoreServiceHistory s:pageList.getList()){
            s.setInvestorUserName(StringUtil.markLastName(s.getInvestorUserName()));
            s.setInvestorUserRealName(StringUtil.markLastName(s.getInvestorUserRealName()));
        }
        return Result.success(pageList);
    }

    /**
     * 1??????????????????????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "???????????????????????????")
    @PostMapping("/manage/service/audit")
    public Result<Boolean> edit(@RequestBody TqStoreServiceInfoAudit req)throws Exception{
        TbDirectStoreOrg org = directStoreOrgService.selectOne(
                new QueryWrapper()
                        .select("partyId")
                        .eq(TsDirectStoreUser.Fields.userId,req.getUserId())
        );
        if(null == org){
            return Result.miss(CommonEnumContainer.ResultStatus.QUERY_DOES_NOT_EXIST,"??????????????????");
        }
        TbDirectStoreServiceHistory serviceInfo = directStoreServiceHistoryService.selectItemByPrimaryKey(IdWrapper.valueOf(req.getServiceId()));
        if(serviceInfo == null || !CommonUtil.isEqual(serviceInfo.getPartyId(),org.getPartyId())){
            return Result.miss(CommonEnumContainer.ResultStatus.QUERY_DOES_NOT_EXIST,"?????????????????????");
        }
        if(!CommonUtil.isEqual(serviceInfo.getAuditResult(), CommonEnumContainer.CustomerServiceAuditResult.TO_AUDIT.getValue())){
            return Result.miss(CommonEnumContainer.ResultStatus.THE_DATA_STATE_IS_INCORRECT,"??????????????????????????????????????????");
        }
        CommonEnumContainer.CustomerServiceAuditResult result = EnumHelperUtil.getByValue(CommonEnumContainer.CustomerServiceAuditResult.class,req.getAuditResult());
        if(null == result){
            return Result.miss(CommonEnumContainer.ResultStatus.QUERY_DOES_NOT_EXIST,"?????????????????????");
        }
        TbDirectStoreServiceHistory up = new TbDirectStoreServiceHistory();
        up.setId(serviceInfo.getId());
        up.setAuditResult(result.getValue());
        directStoreServiceHistoryService.updateByPrimary(new TqDirectStoreServiceHistory().setEntity(up));

        return Result.success(Boolean.TRUE);
    }

    //region ???????????????

    /**
     * ?????????????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "?????????????????????")
    @GetMapping(value="designList")
    public Result<TvPageList<TvDirectStoreDesign>> designList(TqStoreDesignList req){
        MultiTableQueryWrapper queryWrapper =
                WrapperFactory.multiQueryWrapper()
                        .eq(CompareAlias.valueOf("e.appUserId"),req.getUserId());
        if(null != req.getAuditState()){
            queryWrapper.eq(CompareAlias.valueOf(TsDirectStoreDesign.Fields.auditState,"e"),req.getAuditState());
        }
        if(null != req.getVisible()){
            queryWrapper.eq(CompareAlias.valueOf(TsDirectStoreDesign.Fields.visible,"e"),req.getVisible());
        }
        if(null != req.getDeltag()){
            queryWrapper.eq(CompareAlias.valueOf(TsDirectStoreDesign.Fields.deltag,"e"),req.getDeltag());
        }
        queryWrapper
                        .page(req.getPageInfo())
                        .orderBy(OrderBy.valueOf(Order.ASC,SelectAlias.valueOf("e.sort")))
                        .orderBy(OrderBy.valueOf(Order.DESC,SelectAlias.valueOf("e.id")));
        return Result.success(directStoreDesignService.selectPageList(queryWrapper));
    }
    /**
     * ?????????????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "?????????????????????")
    @GetMapping(value="designDetail")
    public Result<TvDirectStoreDesign> designList(TqStoreDesignDetail req){
        MultiTableQueryWrapper queryWrapper =
                WrapperFactory.multiQueryWrapper()
                        .eq(CompareAlias.valueOf("e.id"),req.getId())
                        .eq(CompareAlias.valueOf("e.appUserId"),req.getUserId())
                ;
        return Result.success(directStoreDesignService.selectOneView(queryWrapper));
    }

    /**
     * ????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "????????????")
    @PostMapping(value="removeDesign")
    public Result<Boolean> removeDesign(@RequestBody TqStoreRemoveDesign req){
        if(CollectionUtils.isEmpty(req.getIds())){
            return Result.miss(CommonEnumContainer.ResultStatus.PARAMETER_IS_NOT_COMPLETE,"ids??????");
        }
        directStoreDesignService.updateSelectItem(new UpdateWrapper()
                .update(
                        Elements.valueOf(TsDirectStoreOrg.Fields.deltag,true)
                        ,
                        Elements.valueOf(TsDirectStoreOrg.Fields.updateDate,new Date())
                )
                .in(TsDirectStoreDesign.Fields.id,req.getIds())
                .eq(TsDirectStoreDesign.Fields.appUserId,req.getUserId())
        );
        return Result.success(true);
    }

    /**
     * ????????????????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "????????????????????????")
    @PostMapping(value="returnDesign")
    public Result<Boolean> returnDesign(@RequestBody TqStoreRemoveDesign req){
        if(CollectionUtils.isEmpty(req.getIds())){
            return Result.miss(CommonEnumContainer.ResultStatus.PARAMETER_IS_NOT_COMPLETE,"ids??????");
        }
        directStoreDesignService.updateSelectItem(new UpdateWrapper()
                .update(
                        Elements.valueOf(TsDirectStoreOrg.Fields.deltag,false)
                        ,
                        Elements.valueOf(TsDirectStoreOrg.Fields.updateDate,new Date())
                )
                .in(TsDirectStoreDesign.Fields.id,req.getIds())
                .eq(TsDirectStoreDesign.Fields.appUserId,req.getUserId())
        );
        return Result.success(true);
    }

    /**
     * ??????????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "??????????????????")
    @PostMapping(value="addDesign")
    public Result<Boolean> addDesign(@RequestBody TqStoreAddDesign req){
        TbDirectStoreOrg storeOrg = directStoreOrgService.selectOne(
                WrapperFactory.queryWrapper().eq(TsDirectStoreOrg.Fields.userId,req.getUserId())
                .select(TsDirectStoreOrg.Fields.partyId)
        );
        if(null == storeOrg){
            return Result.miss(CommonEnumContainer.ResultStatus.THIS_RECORD_IS_NO_LONGER_USED,"??????????????????");
        }
        TbDirectStoreDesign entity = new TbDirectStoreDesign();
        entity.setVisible(req.getVisible());
        entity.setContent(req.getContent());
        entity.setTitle(req.getTitle());
        entity.setSubTitle(req.getSubTitle());
        entity.setPartyId(storeOrg.getPartyId());
        entity.setSort(req.getSort());
        entity.setAppUserId(req.getUserId());
        entity.setAuditState(CommonEnumContainer.CustomerAuditState.TO_AUDIT.getValue());
        directStoreDesignService.insert(new TqDirectStoreDesign().setEntity(entity));
        return Result.success(true);
    }

    /**
     * ??????????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "??????????????????")
    @PostMapping(value="editDesign")
    public Result<Boolean> editDesign(@RequestBody TqStoreEditDesign req){
        TbDirectStoreDesign entity = directStoreDesignService.selectOne(
          new QueryWrapper()
                .select(TsDirectStoreDesign.Fields.id)
                .eq(TsDirectStoreDesign.Fields.id,req.getId())
                .eq(TsDirectStoreDesign.Fields.appUserId,req.getUserId())
        );
        if(null == entity){
            return Result.miss(CommonEnumContainer.ResultStatus.THIS_RECORD_IS_NO_LONGER_USED,"???????????????");
        }
        entity.setVisible(req.getVisible());
        entity.setContent(req.getContent());
        entity.setTitle(req.getTitle());
        entity.setSubTitle(req.getSubTitle());
        entity.setSort(req.getSort());
        entity.setAuditState(CommonEnumContainer.CustomerAuditState.TO_SUBMIT_AGAIN.getValue());
        directStoreDesignService.updateByPrimary(new TqDirectStoreDesign().setEntity(entity));
        return Result.success(true);
    }

    /**
     * ??????????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "??????????????????")
    @PostMapping(value="editMultiDesign")
    public Result<Boolean> editDesign(@RequestBody TqStoreDesignMultiEdit req){
        TbDirectStoreDesign entity = new TbDirectStoreDesign();
        for(TqStoreDesignMultiEdit.Entity e:req.getList()){
            BeanUtils.copyProperties(e,entity);
            if(null == e.getId() || e.getId() == 0L){
                entity.setAppUserId(req.getUserId());
                entity.setAuditState(CommonEnumContainer.CustomerAuditState.TO_AUDIT.getValue());
                directStoreDesignService.insert(new TqDirectStoreDesign().setEntity(entity));
            }else{
                if(directStoreDesignService.selectOne(
                        new QueryWrapper()
                                .select(TsDirectStoreDesign.Fields.id)
                                .eq(TsDirectStoreDesign.Fields.id,e.getId())
                                .eq(TsDirectStoreDesign.Fields.appUserId,req.getUserId())
                ) == null){
                    return Result.miss(CommonEnumContainer.ResultStatus.THIS_RECORD_IS_NO_LONGER_USED,"???????????????");
                }
                entity.setAuditState(CommonEnumContainer.CustomerAuditState.TO_SUBMIT_AGAIN.getValue());
                directStoreDesignService.updateByPrimary(new TqDirectStoreDesign().setEntity(entity));
            }
        }
        return Result.success(true);
    }


    //endregion


}

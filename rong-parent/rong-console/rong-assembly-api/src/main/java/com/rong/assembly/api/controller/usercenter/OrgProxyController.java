package com.rong.assembly.api.controller.usercenter;

import com.rong.assembly.api.mapper.RespOrgProxyMapper;
import com.rong.assembly.api.module.request.uc.TqMyRowShowList;
import com.rong.assembly.api.module.request.uc.TqOrgProxy;
import com.rong.assembly.api.module.request.uc.TqUploadRoadShow;
import com.rong.assembly.api.module.request.uc.org.TqOrgProxyCenterIndex;
import com.rong.assembly.api.module.request.usercard.TqSimpleRemoveList;
import com.rong.assembly.api.module.response.org.TrOrgProxyCenterIndex;
import com.rong.cache.base.ViyBasicCache;
import com.rong.cache.service.CommonServiceCache;
import com.rong.cache.service.DictionaryCache;
import com.rong.common.consts.CommonEnumContainer;
import com.rong.common.exception.NoExistsException;
import com.rong.common.module.Result;
import com.rong.common.module.TvPageList;
import com.rong.common.module.UserInfo;
import com.rong.common.util.Assert;
import com.rong.common.util.CommonUtil;
import com.rong.common.util.DateUtil;
import com.rong.common.util.WrapperFactory;
import com.rong.fundmanage.module.view.TvOrgProxy;
import com.rong.fundmanage.service.OrgProxyService;
import com.rong.member.consts.MemEnumContainer;
import com.rong.member.module.entity.TbMemBase;
import com.rong.member.module.query.TsMemBase;
import com.rong.member.service.MemBaseService;
import com.rong.member.util.MemberUtil;
import com.rong.roadshow.module.entity.TbRoadShowInfo;
import com.rong.roadshow.module.query.TsRoadShowInfo;
import com.rong.roadshow.module.request.TqRoadShowInfo;
import com.rong.roadshow.module.view.TvRoadShowInfo;
import com.rong.roadshow.service.RoadShowInfoService;
import com.rong.store.service.DirectStoreOrgService;
import com.vitily.mybatis.core.enums.Order;
import com.vitily.mybatis.core.wrapper.query.IdWrapper;
import com.vitily.mybatis.core.wrapper.query.MultiTableQueryWrapper;
import com.vitily.mybatis.core.wrapper.query.QueryWrapper;
import com.vitily.mybatis.core.wrapper.sort.OrderBy;
import com.vitily.mybatis.util.CompareAlias;
import com.vitily.mybatis.util.Elements;
import com.vitily.mybatis.util.SelectAlias;
import com.vitily.mybatis.util.SnowflakeIdWorker;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;

/**
 * ?????????????????????
 */
@Api(tags = "??????????????????")
@RestController
@RequestMapping("user/orgProxy")
public class OrgProxyController {
    @Autowired
    private CommonServiceCache commonServiceCache;
    @Autowired
    private RoadShowInfoService roadShowInfoService;
    @Autowired
    private MemBaseService memBaseService;
    @Autowired
    private OrgProxyService orgProxyService;
    @Autowired
    DictionaryCache dictionaryCache;
    @Autowired
    ViyBasicCache viyBasicCache;
    @Autowired
    private RespOrgProxyMapper respOrgProxyMapper;
    @Autowired
    private DirectStoreOrgService directStoreOrgService;

    /**
     * ??????????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "??????????????????")
    @GetMapping("manage/index")
    public Result<TrOrgProxyCenterIndex> index(TqOrgProxyCenterIndex req){
        TvOrgProxy proxy = orgProxyService.selectOneView(orgProxyService.getMultiCommonWrapper().eq(
                CompareAlias.valueOf("e.userId"),req.getUserId()
        ));
        if(null == proxy){
            throw new NoExistsException("???????????????????????????");
        }
        QueryWrapper queryWrapper = WrapperFactory.queryWrapper(TrOrgProxyCenterIndex.class)
                .select0(
                        //??????????????????
                        SelectAlias.valueOf("(select count(id) from tb_road_show_info where upload_user_id="+req.getUserId()+" and state = 1) userRoadshowCount",true)
                        ,//??????????????????
                        SelectAlias.valueOf("(select count(id) from tb_road_show_info where org_id="+proxy.getPartyId()+" and state = 1) orgRoadshowCount",true)
                        ,//??????????????????????????????
                        SelectAlias.valueOf("(select sum(show_duration) from tb_road_show_info where upload_user_id="+req.getUserId()+" and state = 1) userRoadshowDuration",true)
                        ,//?????????????????????(??????)
                        SelectAlias.valueOf("(select sum(view_users) from tb_road_show_info where upload_user_id="+req.getUserId()+" and state = 1) userRoadshowPlayback",true)
                        ,//??????????????????????????????
                        SelectAlias.valueOf("(select sum(show_duration) from tb_road_show_info where org_id="+proxy.getPartyId()+" and state = 1) orgRoadshowDuration",true)
                        ,//?????????????????????(??????)
                        SelectAlias.valueOf("(select count(view_users) from tb_road_show_info where org_id="+proxy.getPartyId()+" and state = 1) orgRoadshowPlayback",true)
                        ,//???vip????????????
                        SelectAlias.valueOf("(select end_date from tb_user_vip_end where user_id="+proxy.getUserId()+" and deltag = false limit 1) vipEndDay",true)
                )

                ;

        TrOrgProxyCenterIndex info = respOrgProxyMapper.selectOne(queryWrapper);
        info.setPartyFullName(proxy.getPartyFullName());
        info.setPartyId(proxy.getPartyId());
        info.setPartyShortName(proxy.getPartyShortName());
        return Result.success(info);
    }

    /**
     * ??????????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "??????????????????")
    @GetMapping("/roadShow/list")
    public Result<TvPageList<TvRoadShowInfo>> list(TqMyRowShowList req){
        MultiTableQueryWrapper queryWrapper = roadShowInfoService.getMultiCommonWrapper()
                                    .eq(CompareAlias.valueOf(TsRoadShowInfo.Fields.uploadUserId,"e"),req.getUserId());
        if(null != req.getCateId()){
            queryWrapper.eq(CompareAlias.valueOf(TsRoadShowInfo.Fields.cateId,"e"),req.getCateId());
        }
        if(null != req.getState()){
            queryWrapper.eq(CompareAlias.valueOf(TsRoadShowInfo.Fields.state,"e"),req.getState());
        }
        if(null != req.getLabelId()){
            queryWrapper
                    .or(or->or
                            .eq(CompareAlias.valueOf(TsRoadShowInfo.Fields.labelId,"e"),req.getLabelId())
                            .eq(CompareAlias.valueOf(TsRoadShowInfo.Fields.labelId0,"e"),req.getLabelId())
                            );
        }
        queryWrapper
                .eq(CompareAlias.valueOf("e.deltag"),req.getDeltag())
                .orderBy(OrderBy.valueOf(Order.DESC, SelectAlias.valueOf("e.id")))
                .page(req.getPageInfo());

        TvPageList<TvRoadShowInfo> pageList = roadShowInfoService.selectPageList(queryWrapper);
        return Result.success(pageList);
    }
    /**
     * ????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "????????????")
    @PostMapping("/roadShow/remove")
    public Result<Boolean> removeRoadShow(@RequestBody TqSimpleRemoveList req){
        roadShowInfoService.updateSelectItem(
                WrapperFactory.updateWrapper()
                        .update(
                                Elements.valueOf(TsRoadShowInfo.Fields.deltag,true)
                                ,
                                Elements.valueOf(TsRoadShowInfo.Fields.updateDate,new Date())
                        )
                        .in(TsRoadShowInfo.Fields.id,req.getIds())
                        .eq(TsRoadShowInfo.Fields.uploadUserId,req.getUserId())
        );
        return Result.success(Boolean.TRUE);
    }

    /**
     * 1????????????????????? ?????? ???v ???????????????????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "??????????????????")
    @PostMapping("/roadShow/upload")
    public Result<Boolean> upload(@RequestBody TqUploadRoadShow req, HttpServletRequest request, HttpServletResponse response)throws Exception{


        if(null == req.getCoverImage() || null == req.getVideo()){
            return Result.miss(CommonEnumContainer.ResultStatus.PARAMETER_IS_NOT_COMPLETE,"???????????????????????????");
        }

        //?????????????????????????????????
        CommonServiceCache memCache = MemberUtil.getMemCache(commonServiceCache);
        UserInfo userInfo = memCache.getFromServer(req.getUserAuthToken(), UserInfo.class);
        TbMemBase user = memBaseService.selectItemByPrimaryKey(IdWrapper.valueOf(userInfo.getUserId(),
                TsMemBase.Fields.level, TsMemBase.Fields.id
        ));
        if(!CommonUtil.isEqual(user.getLevel(), MemEnumContainer.MemLevel.??????VIP.getValue())){
            return Result.miss(CommonEnumContainer.ResultStatus.THE_DATA_STATE_IS_INCORRECT,"?????????v?????????????????????????????????");
        }
        TvOrgProxy proxy = orgProxyService.selectOneView(orgProxyService.getMultiCommonWrapper().eq(
                CompareAlias.valueOf("e.userId"),req.getUserId()
        ));
        if(null == proxy){
            throw new NoExistsException("???????????????????????????");
        }

        //???????????????????????????????????????
        int has = roadShowInfoService.selectCount(
                new QueryWrapper()
                    .eq(TsRoadShowInfo.Fields.uploadUserId,req.getUserId())
                    .eq(TsRoadShowInfo.Fields.deltag, CommonEnumContainer.Deltag.NORMAL.getValue())
                    .eq(TsRoadShowInfo.Fields.state, CommonEnumContainer.CustomerAuditState.TO_AUDIT.getValue())
        );
        if(has > 0){
            return Result.miss(CommonEnumContainer.ResultStatus.REPEATING_DATA,"????????????????????????????????????????????????");
        }

        TbRoadShowInfo showInfo = new TbRoadShowInfo().setId(SnowflakeIdWorker.create().nextId());

        showInfo
                .setShowDate(DateUtil.getDate(req.getShowDate(), DateUtil.yyyy_MM_dd_HH_mm_ss_EN))
                .setShowDuration(req.getShowDuration())
                .setCateId(req.getCateId())
                .setLabelId(req.getLabelId())
                .setLabelId0(req.getLabelId0())
                .setDetail(req.getDetail())
                .setCoverImageUrl(req.getCoverImage())
                .setOrgId(proxy.getPartyId())
                .setPresenter(req.getPresenter())
                .setUploadUserId(req.getUserId())
                .setPresenterIntroduce(req.getPresenterIntroduce())
                .setTitle(req.getTitle())
                .setVideoUrl(req.getVideo())
                .setDocUrl(req.getDoc())
                .setDoc2Url(req.getDoc2())
                .setViewUsers(0)
                .setState(CommonEnumContainer.CustomerAuditState.TO_AUDIT.getValue())
                .setHot(false)
                .setTop(false)
                .setFrom(0)
                ;

        TqRoadShowInfo tqRoadShowInfo = new TqRoadShowInfo().setEntity(showInfo);

        roadShowInfoService.insert(tqRoadShowInfo);

        return Result.success(Boolean.TRUE);
    }

    /**
     * 1?????????????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "??????????????????")
    @PostMapping("/roadShow/edit")
    public Result<Boolean> editRoadShow(@RequestBody TqUploadRoadShow req, HttpServletRequest request, HttpServletResponse response)throws Exception{

        if(null == req.getId()){
            return Result.miss(CommonEnumContainer.ResultStatus.PARAMETER_IS_NOT_COMPLETE,"??????id????????????");
        }
        TbRoadShowInfo roadShow = roadShowInfoService.selectOne(WrapperFactory.queryWrapper()
                .eq(TsRoadShowInfo.Fields.id,req.getId())
                .eq(TsRoadShowInfo.Fields.uploadUserId,req.getUserId())
        );
        Assert.notNull(roadShow,"???????????????");
        TbRoadShowInfo up = new TbRoadShowInfo();
        up.setUpdateDate(new Date());
        if(null != req.getCoverImage()){
            up.setCoverImageUrl(req.getCoverImage());
        }
        if(null != req.getVideo()){
            up.setVideoUrl(req.getVideo());
        }
        //????????????
        if(null != req.getDoc()) {
            up.setDocUrl(req.getDoc());
        }

        //????????????2
        if(null != req.getDoc2()) {
            up.setDoc2Url(req.getDoc2());
        }
        up.setId(req.getId());
        up.setState(CommonEnumContainer.CustomerAuditState.TO_SUBMIT_AGAIN.getValue());
        up.setLabelId0(req.getLabelId0());
        up.setLabelId(req.getLabelId());
        up.setCateId(req.getCateId())
                .setShowDate(DateUtil.getDate(req.getShowDate(), DateUtil.yyyy_MM_dd_HH_mm_ss_EN))
                .setShowDuration(req.getShowDuration())
                .setDetail(req.getDetail())
                .setTitle(req.getTitle())
                .setPresenter(req.getPresenter())
                .setPresenterIntroduce(req.getPresenterIntroduce())
                ;

        roadShowInfoService.updateByPrimary(new TqRoadShowInfo().setEntity(up));

        return Result.success(Boolean.TRUE);
    }

    /**
     * ???????????????????????????
     * @param req
     * @return
     */
    @ApiOperation(value = "?????????????????????")
    @GetMapping(value="orgProxy")
    public Result<List<TvOrgProxy>> orgProxy(TqOrgProxy req){
        //?????????????????????????????????
        CommonServiceCache memCache = MemberUtil.getMemCache(commonServiceCache);
        UserInfo userInfo = memCache.getFromServer(req.getUserAuthToken(), UserInfo.class);
        List<TvOrgProxy> orgProxies = orgProxyService.selectViewList(
                orgProxyService.getMultiCommonWrapper().eq(CompareAlias.valueOf("e.userId"),req.getUserId())
        );
        return Result.success(orgProxies);
    }


}

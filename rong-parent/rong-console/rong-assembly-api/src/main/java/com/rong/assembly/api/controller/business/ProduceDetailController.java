package com.rong.assembly.api.controller.business;

import com.rong.assembly.api.module.request.*;
import com.rong.assembly.api.module.response.TrProduceCompare;
import com.rong.assembly.api.service.OrgService;
import com.rong.assembly.api.service.PeopleService;
import com.rong.cache.service.CommonServiceCache;
import com.rong.common.consts.BusinessEnumContainer;
import com.rong.common.consts.CommonEnumContainer;
import com.rong.common.module.Result;
import com.rong.common.module.TqBase;
import com.rong.common.module.TvPageList;
import com.rong.common.util.StringUtil;
import com.rong.member.util.MemberUtil;
import com.rong.store.module.query.TsDirectStoreUser;
import com.rong.store.module.view.TvDirectStoreUser;
import com.rong.store.service.DirectStoreUserService;
import com.rong.tong.fund.service.FundClassService;
import com.rong.tong.pfunds.module.view.TvDateBoundary;
import com.rong.tong.pfunds.module.view.TvFundStyle;
import com.rong.tong.pfunds.module.view.TvHisNav;
import com.rong.tong.pfunds.module.view.TvIntervalReturn;
import com.rong.tong.pfunds.module.view.TvMaxDrawdownTrend;
import com.rong.tong.pfunds.module.view.TvPfundMktHisNav;
import com.rong.tong.pfunds.module.view.TvPfundMktList;
import com.rong.tong.pfunds.module.view.TvProduceInformation;
import com.rong.tong.pfunds.module.view.TvProduceSummary;
import com.rong.tong.pfunds.module.view.TvRiskAssessment;
import com.rong.tong.pfunds.module.view.TvYearMonReturn;
import com.rong.tong.pfunds.service.BusinessService;
import com.vitily.mybatis.core.wrapper.PageInfo;
import com.vitily.mybatis.util.CompareAlias;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description TODO
 * @Author ludexin
 * @Date 2020-01-16 14:56
 **/
@Api(tags = "????????????")
@RestController
@RequestMapping("/produce")
public class ProduceDetailController {

    @Autowired
    private BusinessService businessService;

    @Autowired
    private FundClassService fundClassService;

    @Autowired
    private DirectStoreUserService directStoreUserService;

    @Autowired
    private CommonServiceCache commonServiceCache;

    @Autowired
    private PeopleService peopleService;

    @Autowired
    private OrgService orgService;
    /**
     * ??????
     *
     * @param req
     * @return
     */
    @ApiOperation(value = "??????")
    @GetMapping("summary")
    public Result<TvProduceSummary> summary(TqProduceDetail req) {
        BusinessEnumContainer.FundType type = businessService.getFundType(req.getSecurityId());
        TvProduceSummary summary;
        if (type == BusinessEnumContainer.FundType.??????) {
            summary = businessService.getFundInfoSummary(req.getSecurityId(), req.getLoginUserId());
        } else {
            summary = fundClassService.getFundInfoSummary(req.getSecurityId(), req.getLoginUserId());
        }
        if (summary == null) {
            return Result.success();
        }
        if (summary.getStoreUser() != null && summary.getStoreUser()) {
            CommonServiceCache memCache = MemberUtil.getMemCache(commonServiceCache);
            //??????????????????
            List<TvDirectStoreUser> storeUsers = directStoreUserService.selectViewList(directStoreUserService.getMultiCommonWrapper()
                    .eq(CompareAlias.valueOf(TsDirectStoreUser.Fields.partyId), summary.getPartyId())
                    .eq(CompareAlias.valueOf("e.type"), CommonEnumContainer.StoreUserType.SERVICE.getValue())
                    .eq(CompareAlias.valueOf(TsDirectStoreUser.Fields.state, "e"), CommonEnumContainer.CustomerAuditState.GET_APPROVED.getValue())
                    .eq(CompareAlias.valueOf("e.deltag"), CommonEnumContainer.Deltag.NORMAL.getValue())
            );
            for(TvDirectStoreUser m:storeUsers){
                m.setOnlineState(memCache.existsInServer(m.getType()+"-" + m.getUserId())?
                        CommonEnumContainer.OnlineState.ON_LINE.getValue(): CommonEnumContainer.OnlineState.NOT_ONLINE.getValue());
            }
            summary.setCustomerServers(storeUsers);
        }
        return Result.success(summary);
    }

    /**
     * ????????????
     *
     * @param req
     * @return
     */
    @ApiOperation(value = "????????????")
    @GetMapping("hisNav")
    public Result<TvPageList<TvHisNav>> hisNav(TqHisNav req) {
        BusinessEnumContainer.FundType type = businessService.getFundType(req.getSecurityId());
        if (type == BusinessEnumContainer.FundType.??????) {
            return Result.success(businessService.hisNav(req.getPageInfo() == null ? new PageInfo() : req.getPageInfo(), req.getSecurityId()));
        }
        return Result.success(fundClassService.hisNav(req.getPageInfo() == null ? new PageInfo() : req.getPageInfo(), req.getSecurityId()));
    }

    /**
     * ????????????????????????
     *
     * @param req
     * @return
     */
    @ApiOperation(value = "????????????????????????")
    @GetMapping("hisNavMaxDate")
    public Result<TvDateBoundary> hisNavDateBoundary(TqProduceDetail req) {
        BusinessEnumContainer.FundType type = businessService.getFundType(req.getSecurityId());
        if (type == BusinessEnumContainer.FundType.??????) {
            return Result.success(businessService.hisNavDateBoundary(req.getSecurityId()));
        }
        return Result.success(fundClassService.hisNavDateBoundary(req.getSecurityId()));
    }


    /**
     * ???????????????
     *
     * @param req
     * @return
     */
    @ApiOperation(value = "???????????????")
    @GetMapping("accumNavTrend")
    public Result<List<TvHisNav>> accumNavTrend(TqAccumNavTrend req) {
        BusinessEnumContainer.FundType type = businessService.getFundType(req.getSecurityId());
        if (type == BusinessEnumContainer.FundType.??????) {
            return Result.success(businessService.accumNavTrend(req.getStartDate(), req.getEndDate(), req.getSecurityId()));
        }
        return Result.success(fundClassService.accumNavTrend(req.getStartDate(), req.getEndDate(), req.getSecurityId()));
    }

    /**
     * ????????????
     *
     * @param req
     * @return
     */
    @ApiOperation(value = "????????????")
    @GetMapping("maxDrawdownTrend")
    public Result<List<TvMaxDrawdownTrend>> maxDrawdownTrend(TqProduceDetail req) {
        BusinessEnumContainer.FundType type = businessService.getFundType(req.getSecurityId());
        if (type == BusinessEnumContainer.FundType.??????) {
            return Result.success(businessService.maxDrawdownTrend(req.getSecurityId()));
        }
        return Result.success(fundClassService.maxDrawdownTrend(req.getSecurityId()));
    }

    /**
     * ??????????????????
     *
     * @param req
     * @return
     */
    @ApiOperation(value = "??????????????????")
    @GetMapping("maxHisDrawdown")
    public Result<TvMaxDrawdownTrend> maxHisDrawdown(TqProduceDetail req) {
        BusinessEnumContainer.FundType type = businessService.getFundType(req.getSecurityId());
        if (type == BusinessEnumContainer.FundType.??????) {
            return Result.success(businessService.maxHisDrawdown(req.getSecurityId()));
        }
        return Result.success(fundClassService.maxHisDrawdown(req.getSecurityId()));
    }

    /**
     * ????????????
     *
     * @param req
     * @return
     */
    @ApiOperation(value = "????????????")
    @GetMapping("intervalReturn")
    public Result<TvIntervalReturn> intervalReturn(TqProduceDetail req) {
        BusinessEnumContainer.FundType type = businessService.getFundType(req.getSecurityId());
        if (type == BusinessEnumContainer.FundType.??????) {
            return Result.success(businessService.getIntervalReturn(req.getSecurityId()));
        }
        return Result.success(fundClassService.getIntervalReturn(req.getSecurityId()));
    }

    /**
     * ????????????
     *
     * @param req
     * @return
     */
    @ApiOperation(value = "????????????")
    @GetMapping("yearReturn")
    public Result<List<TvYearMonReturn>> yearReturn(TqProduceDetail req) {
        BusinessEnumContainer.FundType type = businessService.getFundType(req.getSecurityId());
        if (type == BusinessEnumContainer.FundType.??????) {
            return Result.success(businessService.getYearReturn(req.getSecurityId()));
        }
        return Result.success(fundClassService.getYearReturn(req.getSecurityId()));
    }

    /**
     * ????????????
     *
     * @param req
     * @return
     */
    @ApiOperation(value = "????????????")
    @GetMapping("monReturn")
    public Result<List<TvYearMonReturn>> monReturn(TqProduceDetail req) {
        BusinessEnumContainer.FundType type = businessService.getFundType(req.getSecurityId());
        if (type == BusinessEnumContainer.FundType.??????) {
            return Result.success(businessService.getMonReturn(req.getSecurityId()));
        }
        return Result.success(fundClassService.getMonReturn(req.getSecurityId()));
    }


    /**
     * ????????????(???????????????)
     *
     * @param req
     * @return
     */
    @ApiOperation(value = "????????????(???????????????)")
    @GetMapping("riskAssessment")
    public Result<List<TvRiskAssessment>> riskAssessment(TqProduceDetail req) {
        BusinessEnumContainer.FundType type = businessService.getFundType(req.getSecurityId());
        if (type == BusinessEnumContainer.FundType.??????) {
            return Result.success(businessService.riskAssessment(req.getSecurityId()));
        }
        return Result.success(fundClassService.riskAssessment(req.getSecurityId()));
    }

    /**
     * ????????????
     *
     * @param req
     * @return
     */
    @ApiOperation(value = "????????????")
    @GetMapping("fundStyle")
    public Result<List<TvFundStyle>> fundStyle(TqProduceDetail req) {
        BusinessEnumContainer.FundType type = businessService.getFundType(req.getSecurityId());
        if (type == BusinessEnumContainer.FundType.??????) {
            return Result.success(businessService.fundStyle(req.getSecurityId()));
        }
        return Result.success(fundClassService.fundStyle(req.getSecurityId()));
    }

    /**
     * ????????????
     *
     * @param req
     * @return
     */
    @ApiOperation(value = "????????????")
    @GetMapping("produceInformation")
    public Result<TvProduceInformation> produceInformation(TqProduceDetail req) {
        BusinessEnumContainer.FundType type = businessService.getFundType(req.getSecurityId());
        if (type == BusinessEnumContainer.FundType.??????) {
            return Result.success(businessService.produceInformation(req.getSecurityId()));
        }
        return Result.success(fundClassService.produceInformation(req.getSecurityId()));
    }

    /**
     * ????????????
     *
     * @param req
     * @return
     */
    @ApiOperation(value = "????????????")
    @GetMapping("produceCompare")
    public Result<List<TrProduceCompare>> produceCompare(TqProduceCompare req) {
        ArrayList<TrProduceCompare> results = new ArrayList<>();
        if (StringUtil.isNotEmpty(req.getSecurityIds())) {
            String[] securityIds = req.getSecurityIds().split(",");
            for (String securityId: securityIds) {
                Integer intSecurityId = Integer.valueOf(securityId);
                BusinessEnumContainer.FundType type = businessService.getFundType(intSecurityId);
                if (type == BusinessEnumContainer.FundType.??????) {
                    TrProduceCompare produceCompare = new TrProduceCompare();
                    produceCompare.setProduceSummary(businessService.getFundInfoSummary(intSecurityId, -1L));
                    produceCompare.setProduceInformation(businessService.produceInformation(intSecurityId));
                    TqPeopleInfo peopleReq = new TqPeopleInfo();
                    peopleReq.setPersonId(produceCompare.getProduceSummary().getPersonId());
                    produceCompare.setManager(peopleService.info(peopleReq));
                    TqOrgInfo orgReq = new TqOrgInfo();
                    if (produceCompare.getProduceSummary().getPartyId() != null) {
                        orgReq.setPartyId(Long.valueOf(produceCompare.getProduceSummary().getPartyId()));
                        produceCompare.setOrg(orgService.selectPriOrgInfo(orgReq));
                    }
                    produceCompare.setHisNavs(businessService.accumNavTrend(req.getStartDate(), req.getEndDate(), intSecurityId));
                    produceCompare.setDateBoundary(businessService.hisNavDateBoundary(intSecurityId));
                    produceCompare.setIntervalReturn(businessService.getIntervalReturn(intSecurityId));
                    results.add(produceCompare);
                } else {
                    TrProduceCompare produceCompare = new TrProduceCompare();
                    produceCompare.setProduceSummary(fundClassService.getFundInfoSummary(intSecurityId, -1L));
                    produceCompare.setProduceInformation(fundClassService.produceInformation(intSecurityId));
                    TqPeopleInfo peopleReq = new TqPeopleInfo();
                    peopleReq.setPersonId(produceCompare.getProduceSummary().getPersonId());
                    produceCompare.setManager(peopleService.info(peopleReq));
                    TqOrgInfo orgReq = new TqOrgInfo();
                    if (produceCompare.getProduceSummary().getPartyId() != null) {
                        orgReq.setPartyId(Long.valueOf(produceCompare.getProduceSummary().getPartyId()));
                        produceCompare.setOrg(orgService.selectRaisedOrgInfo(orgReq));
                    }
                    produceCompare.setHisNavs(fundClassService.accumNavTrend(req.getStartDate(), req.getEndDate(), intSecurityId));
                    produceCompare.setDateBoundary(fundClassService.hisNavDateBoundary(intSecurityId));
                    produceCompare.setIntervalReturn(fundClassService.getIntervalReturn(intSecurityId));
                    results.add(produceCompare);
                }

            }
        }
        return Result.success(results);
    }


    /**
     * ????????????????????????
     *
     * @param
     * @return
     */
    @ApiOperation(value = "????????????????????????")
    @GetMapping("pfundMktList")
    public Result<List<TvPfundMktList>> pfundMktList(TqBase req) {
        return Result.success(businessService.pfundMktList());

    }

    /**
     * ???????????????????????????
     *
     * @param req
     * @return
     */
    @ApiOperation(value = "???????????????????????????")
    @GetMapping("pfundMktHis")
    public Result<List<TvPfundMktHisNav>> pfundMktHis(TqAccumNavTrend req) {
        return Result.success(businessService.pfundMktHis(req.getStartDate(), req.getEndDate(), req.getSecurityId()));
    }
}

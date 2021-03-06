package com.rong.member.service.impl;

import com.rong.common.consts.CommonEnumContainer;
import com.rong.common.service.impl.BasicServiceImpl;
import com.rong.common.util.*;
import com.rong.member.mapper.MemBaseMapper;
import com.rong.member.mapper.MemCompanyUserinfoMapper;
import com.rong.member.mapper.MemPersonalUserinfoMapper;
import com.rong.member.mapper.UserAccountMapper;
import com.rong.member.module.entity.TbMemBase;
import com.rong.member.module.entity.TbMemCompanyUserinfo;
import com.rong.member.module.entity.TbMemPersonalUserinfo;
import com.rong.member.module.entity.TbUserAccount;
import com.rong.member.module.query.TsMemBase;
import com.rong.member.module.query.TsUserAccount;
import com.rong.member.module.request.TqMemBase;
import com.rong.member.module.view.TvMemBase;
import com.rong.member.service.MemBaseService;
import com.rong.member.util.MemberUtil;
import com.rong.user.mapper.InvestorQualifiedMapper;
import com.rong.user.module.entity.TbInvestorQualified;
import com.rong.user.module.query.TsInvestorQualified;
import com.vitily.mybatis.core.wrapper.query.MultiTableIdWrapper;
import com.vitily.mybatis.core.wrapper.query.QueryWrapper;
import com.vitily.mybatis.util.SnowflakeIdWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class MemBaseServiceImpl extends BasicServiceImpl<TbMemBase, TqMemBase, TvMemBase, MemBaseMapper> implements MemBaseService {

    private final MemPersonalUserinfoMapper memPersonalUserinfoMapper;
    private final MemCompanyUserinfoMapper memCompanyUserinfoMapper;
    private final UserAccountMapper userAccountMapper;

    @Autowired
    private InvestorQualifiedMapper investorQualifiedMapper;

    @Autowired()
    public MemBaseServiceImpl(MemPersonalUserinfoMapper memPersonalUserinfoMapper,
                              MemCompanyUserinfoMapper memCompanyUserinfoMapper,
                              UserAccountMapper userAccountMapper
    ) {
        this.memPersonalUserinfoMapper = memPersonalUserinfoMapper;
        this.memCompanyUserinfoMapper = memCompanyUserinfoMapper;
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    public TvMemBase selectViewByPrimaryKey(MultiTableIdWrapper wrapper){
        TvMemBase view = super.selectViewByPrimaryKey(wrapper);
        view.setInvestorQualified(investorQualifiedMapper.selectOne(
                new QueryWrapper()
                .eq(TsInvestorQualified.Fields.userId, view.getId())
        ));
        return view;
    }

    @Override
    public String encryPassword(TbMemBase entity, String customerPassword) {
        return MD5.getMD5(customerPassword + entity.getSalt());
    }

    @Override
    public String createRecommendCode(TbMemBase entity) {
        return MD5.getMD5(entity.getUserName() + entity.getSalt());
    }

    @Override
    protected final void beforeInsert(TqMemBase req) {
        TbMemBase entity = req.getEntity();
        //?????????????????????????????????
        Assert.isNull(mapper.selectOne(
                WrapperFactory.queryWrapper().or(or->or
                        .eq(TsMemBase.Fields.userName,entity.getUserName())
                        .eq(TsMemBase.Fields.phone,entity.getPhone())
                )
        ),"?????????????????????????????????");

        //??????
        MemberUtil.checkMemberName(entity.getUserName(), entity.getUserName(), entity.getEmail());
        entity.setId(SnowflakeIdWorker.create().nextId());

        if (StringUtil.isNotEmpty(entity.getPassword())) {//??????????????????salt???token key???????????????
            entity.setSalt(GUIDGenerator.getGUID());
            entity.setPassword(encryPassword(entity, entity.getPassword()));
            entity.setTokenKey(GUIDGenerator.getGUID());
        }
        Date now = new Date();
        entity.setCreateDate(now);
        entity.setUpdateDate(now);
        entity.setDeltag(CommonEnumContainer.Deltag.NORMAL.getValue());
        entity.setRecommendCode(createRecommendCode(entity));//?????????

    }

    @Override
    protected final void afterInsert(TqMemBase req) {
        TbMemBase entity = req.getEntity();
        TbUserAccount account = new TbUserAccount();
        account.setUserId(entity.getId())
                .setDeltag(CommonEnumContainer.Deltag.NORMAL.getValue())
                .setCreateDate(req.getEntity().getCreateDate());
        //??????????????????
        userAccountMapper.insertSelective(account);
        //????????????????????????(??????/???????????????????????????????????????????????????????????????)
        TbMemCompanyUserinfo companyUserinfo = req.getCompanyUserinfo();
        if (CommonUtil.isNull(companyUserinfo)) {
            companyUserinfo = new TbMemCompanyUserinfo();
        }
        companyUserinfo.setId(entity.getId());
        memCompanyUserinfoMapper.insertSelective(companyUserinfo);

        TbMemPersonalUserinfo personalUserinfo = req.getPersonalUserinfo();
        if (CommonUtil.isNull(personalUserinfo)) {
            personalUserinfo = new TbMemPersonalUserinfo();
        }
        personalUserinfo.setId(entity.getId());
        memPersonalUserinfoMapper.insertSelective(personalUserinfo);
    }

    @Override
    protected final void beforeUpdate(TqMemBase req) {
        TbMemBase entity = req.getEntity();
        MemberUtil.checkMemberName(entity.getUserName(), entity.getUserName(), entity.getEmail());
        if (StringUtil.isEmpty(entity.getPassword())) {//??????????????????salt???token key???????????????
            entity.setPassword(null);
            entity.setSalt(null);
            entity.setTokenKey(null);
        } else {//??????????????????token key????????????
            entity.setSalt(GUIDGenerator.getGUID());
            entity.setPassword(encryPassword(entity, entity.getPassword()));
            entity.setTokenKey(GUIDGenerator.getGUID());
        }
    }

    @Override
    protected final void afterUpdate(TqMemBase req) {
        //????????????????????????
        if (CommonUtil.isNotNull(req.getCompanyUserinfo())) {
            req.getCompanyUserinfo().setId(req.getEntity().getId());
            memCompanyUserinfoMapper.updateSelectiveByPrimaryKey(req.getCompanyUserinfo());
        }
        if (CommonUtil.isNotNull(req.getPersonalUserinfo())) {
            req.getPersonalUserinfo().setId(req.getEntity().getId());
            memPersonalUserinfoMapper.updateSelectiveByPrimaryKey(req.getPersonalUserinfo());
        }
    }
}
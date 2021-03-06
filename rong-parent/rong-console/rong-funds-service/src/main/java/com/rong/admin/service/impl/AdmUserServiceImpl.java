package com.rong.admin.service.impl;

import com.rong.admin.consts.AdmConstValue;
import com.rong.admin.mapper.AdmUserMapper;
import com.rong.admin.module.entity.TbAdmRole;
import com.rong.admin.module.entity.TbAdmUser;
import com.rong.admin.module.foreign.UserStorage;
import com.rong.admin.module.query.TsAdmRole;
import com.rong.admin.module.query.TsAdmUser;
import com.rong.admin.module.request.TqAdmUser;
import com.rong.admin.module.view.TvAdmUser;
import com.rong.admin.service.AdmUserService;
import com.rong.cache.service.CacheSerializableDelegate;
import com.rong.cache.service.CommonServiceCache;
import com.rong.common.consts.CommonEnumContainer;
import com.rong.common.consts.DictionaryKey;
import com.rong.common.exception.CustomerException;
import com.rong.common.service.impl.BasicServiceImpl;
import com.rong.common.util.*;
import com.vitily.mybatis.core.consts.ConstValue;
import com.vitily.mybatis.core.wrapper.query.IdWrapper;
import com.vitily.mybatis.core.wrapper.query.MultiTableIdWrapper;
import com.vitily.mybatis.core.wrapper.query.MultiTableQueryWrapper;
import com.vitily.mybatis.core.wrapper.query.QueryWrapper;
import com.vitily.mybatis.util.ClassAssociateTableInfo;
import com.vitily.mybatis.util.CompareAlias;
import com.vitily.mybatis.util.SelectAlias;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Service
public class AdmUserServiceImpl extends BasicServiceImpl<TbAdmUser, TqAdmUser,TvAdmUser, AdmUserMapper> implements AdmUserService {

    private final CommonServiceCache commonServiceCache;
    @Autowired
    public AdmUserServiceImpl(CommonServiceCache commonServiceCache) {
        this.commonServiceCache = commonServiceCache;
    }

    @Override
    public MultiTableQueryWrapper getMultiCommonWrapper(){
        SelectAlias[] facs = new SelectAlias[TsAdmUser.Fields.values().length];
        int i = 0;
        for(TsAdmUser.Fields f:TsAdmUser.Fields.values()){
            facs[i++] = SelectAlias.valueOf(f, ConstValue.MAIN_ALIAS);
        }

        return new MultiTableQueryWrapper()
                .select0(facs)
                .select("a.permissionStr rolePermissionStr,a.name roleName")
                .leftJoin(ClassAssociateTableInfo.valueOf(TbAdmRole.class,"a"),
                        x->x.eqc(
                                CompareAlias.valueOf(TsAdmRole.Fields.id,"a"),
                                CompareAlias.valueOf(TsAdmUser.Fields.roleId,"e")))
                ;
    }
    @Override
    public MultiTableIdWrapper getMultiCommonIdWrapper(Object id) {
        return MultiTableIdWrapper.valueOf(id, getMultiCommonWrapper());
    }

    @Override
    protected void beforeInsert(TqAdmUser req) {
        super.beforeInsert(req);
        if (StringUtil.isNotEmpty(req.getEntity().getPassword())) {
            req.getEntity().setSalt(GUIDGenerator.getGUID());
            req.getEntity().setPassword(encryPassword(req.getEntity(), req.getEntity().getPassword()));
            req.getEntity().setAuthToken(GUIDGenerator.getGUID());
        }
    }

    @Override
    protected final void beforeUpdate(TqAdmUser req){
        Assert.notEqual(req.getEntity().getId(), AdmConstValue.SUP_ADMIN_ID,"????????????????????????????????????");

        //??????????????????0 ???????????????????????????????????????salt?????????, token ???????????????
        if (StringUtil.isEmpty(req.getEntity().getPassword())) {
            req.getEntity().setPassword(null);
            req.getEntity().setSalt(null);
            req.getEntity().setAuthToken(null);
        } else {
            req.getEntity().setSalt(GUIDGenerator.getGUID());
            req.getEntity().setPassword(encryPassword(req.getEntity(), req.getEntity().getPassword()));
            req.getEntity().setAuthToken(GUIDGenerator.getGUID());
        }
    }
    @Override
    protected final void afterUpdate(TqAdmUser req){
        TvAdmUser admUser = mapper.selectViewByPrimaryKey(getMultiCommonIdWrapper(req.getEntity().getId()));

        //?????????????????????????????????
        String keyToken = encryToken(admUser);
        UserStorage storage = commonServiceCache.getInstance(DictionaryKey.Keys.BACKGROUND_USER_TOKEN, CacheSerializableDelegate.jsonSeriaze()).getFromServer(keyToken,UserStorage.class);
        if (CommonUtil.isNotNull(storage)) {
            //
            storage.setShowName(admUser.getNickName());
            storage.setPermissions(getTotalPermissions(admUser));
            commonServiceCache.getInstance(DictionaryKey.Keys.BACKGROUND_USER_TOKEN,CacheSerializableDelegate.jsonSeriaze()).upToServer(keyToken,storage);
        }
    }

    @Override
    public void updateByPassword(TbAdmUser admin, String newPassword, int durationOfDay){
        TbAdmUser user = mapper.selectItemByPrimaryKey(IdWrapper.valueOf(admin.getId()));
        //??????????????????????????????
        Assert.equal(user.getPassword(),encryPassword(user,admin.getPassword()),"??????????????????????????????????????????????????????");
        //????????????????????????null????????????0
        if(StringUtil.isEmpty(newPassword)){
            admin.setSalt(null);
            admin.setPassword(null);
            admin.setAuthToken(null);
            admin.setPasswordExpiration(null);
        }else{
            admin.setSalt(GUIDGenerator.getGUID());
            admin.setPassword(encryPassword(admin, newPassword));
            //?????????????????????token????????????
            admin.setAuthToken(GUIDGenerator.getGUID());
            Date now = new Date();
            //30?????????
            admin.setPasswordExpiration(DateUtil.addDate(now, durationOfDay));
        }
        //????????????????????????
        admin.setUserName(null);
        //??????????????????????????????
        admin.setRoleId(null);
        //?????????????????????
        admin.setPermissionStr(null);
        admin.setUpdateDate(new Date());


        Assert.isTrue(mapper.selectCount(
                new QueryWrapper()
                        .eq(TsAdmUser.Fields.nickName,admin.getNickName())
                        .neq(TsAdmUser.Fields.id,user.getId())) == 0
        ,"????????????????????????");
        mapper.updateSelectiveByPrimaryKey(admin);
    }

    /**
     * ?????????????????????
     * @param entity 1
     * @return 2
     */
    @Override
    public String encryPassword(TbAdmUser entity, String customerPassword){
        return MD5.getMD5(customerPassword+entity.getSalt());
    }


    /**
     * ??????token???
     * @param entity 1
     * @return 2
     */
    @Override
    public String encryToken(TbAdmUser entity){
        return MD5.getMD5(entity.getSalt()+entity.getId()+entity.getAuthToken());
    }
    @Override
    public void checkAdmin(TvAdmUser admin){
        if(CommonUtil.isNull(admin)){
            throw new CustomerException("??????????????????", CommonEnumContainer.ResultStatus.QUERY_DOES_NOT_EXIST);
        }
        //????????????????????????
        if(!admin.getDeltag().equals(CommonEnumContainer.Deltag.NORMAL.getValue())){
            throw new CustomerException("??????????????????", CommonEnumContainer.ResultStatus.QUERY_DOES_NOT_EXIST);
        }
        //???????????????????????????
        if(!CommonUtil.isEqual(admin.getState(), CommonEnumContainer.State.NORMAL.getValue())){
            throw new CustomerException("??????????????????", CommonEnumContainer.ResultStatus.THE_USER_DOES_NOT_EXIST);
        }
        //?????????????????????:null??????????????????
        if(CommonUtil.isNull(admin.getPasswordExpiration()) || new Date().getTime() > admin.getPasswordExpiration().getTime()){
            throw new CustomerException("?????????????????????????????????????????????", CommonEnumContainer.ResultStatus.TOKEN_IS_INVALID);
        }
    }

    @Override
    public Set<Long> getTotalPermissions(TvAdmUser admin){
        Set<Long> sets = new HashSet<>();
        if(StringUtil.isNotEmpty(admin.getPermissionStr())){
            String[] str = admin.getPermissionStr().split(",");
            for(String s:str){
                if (StringUtil.isNotEmpty(s)) {
                    sets.add(Long.valueOf(s));
                }
            }
        }
        if(StringUtil.isNotEmpty(admin.getRolePermissionStr())){
            String[] str = admin.getRolePermissionStr().split(",");
            for(String s:str){
                if (StringUtil.isNotEmpty(s)) {
                    sets.add(Long.valueOf(s));
                }
            }
        }
        return sets;
    }
}
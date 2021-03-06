package com.rong.console.center.config;

import com.rong.admin.module.entity.TbAdmUser;
import com.rong.admin.module.foreign.UserStorage;
import com.rong.admin.module.query.TsAdmUser;
import com.rong.admin.module.req.TqUserLogin;
import com.rong.admin.module.view.TvAdmUser;
import com.rong.admin.service.AdmColumnService;
import com.rong.admin.service.AdmUserService;
import com.rong.cache.service.CacheSerializableDelegate;
import com.rong.cache.service.ComServiceFrequentCache;
import com.rong.cache.service.CommonServiceCache;
import com.rong.cache.service.DictionaryCache;
import com.rong.common.consts.CommonEnumContainer;
import com.rong.common.consts.DictionaryKey;
import com.rong.common.exception.CustomerException;
import com.rong.common.exception.NoLoginException;
import com.rong.common.exception.NoPermissionException;
import com.rong.common.util.CommonUtil;
import com.rong.common.util.GUIDGenerator;
import com.rong.common.util.NumberUtil;
import com.rong.console.center.util.CookieHelper;
import com.vitily.mybatis.util.CompareAlias;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

@Component
@Aspect
@Order(value = 8098)
public class AuthenticationInterceptor {
    private final CommonServiceCache commonServiceCache;
    private final AdmColumnService admColumnService;
    private final DictionaryCache dictionaryCache;
    private final ComServiceFrequentCache comServiceFrequentCache;
    private final AdmUserService admUserService;

    @Autowired
    public AuthenticationInterceptor(
            CommonServiceCache commonServiceCache,
            AdmColumnService admColumnService,
            DictionaryCache dictionaryCache,
            ComServiceFrequentCache comServiceFrequentCache,
            AdmUserService admUserService
    ) {
        this.commonServiceCache = commonServiceCache;
        this.admColumnService = admColumnService;
        this.dictionaryCache = dictionaryCache;
        this.comServiceFrequentCache = comServiceFrequentCache;
        this.admUserService = admUserService;
    }

    @Pointcut("@annotation(org.springframework.web.bind.annotation.RequestMapping) || @annotation(org.springframework.web.bind.annotation.GetMapping) || @annotation(org.springframework.web.bind.annotation.PostMapping)")
    public void controllerAspect() {
    }

    public UserStorage login(TqUserLogin req) {

        //??????????????????????????????????????????
        Integer maxErrorCount = NumberUtil.toInteger(dictionaryCache.getValue(DictionaryKey.Keys.MAXIMUM_NUMBER_OF_BACKGROUND_LOGIN_ATTEMPTS.getValue()));

        int errCount = comServiceFrequentCache.getCache(DictionaryKey.MemServiceKeyType.NUMBER_OF_BACKGROUND_USER_LOGIN_ATTEMPTS, req.getUserName());

        if (CommonUtil.isNull(maxErrorCount) || maxErrorCount.compareTo(errCount) <= 0) {
            maxErrorCount = 10;
        }

        int left = maxErrorCount - errCount;
        if (left <= 0) {
            throw new CustomerException("???????????????????????????????????????????????????????????????????????????????????????].", CommonEnumContainer.ResultStatus.ABNORMAL_OPERATION);
        }
        TvAdmUser entity = admUserService.selectOneView(
        		admUserService.getMultiCommonWrapper()
				.eq(CompareAlias.valueOf(TsAdmUser.Fields.userName),req.getUserName()));

        admUserService.checkAdmin(entity);
		if(CommonUtil.isNull(entity)){
			throw new CustomerException("??????????????????", CommonEnumContainer.ResultStatus.QUERY_DOES_NOT_EXIST);
		}
		//????????????????????????
		if(!entity.getDeltag().equals(CommonEnumContainer.Deltag.NORMAL.getValue())){
			throw new CustomerException("??????????????????", CommonEnumContainer.ResultStatus.QUERY_DOES_NOT_EXIST);
		}
		//???????????????????????????
		if(!CommonUtil.isEqual(entity.getState(), CommonEnumContainer.State.NORMAL.getValue())){
			throw new CustomerException("??????????????????", CommonEnumContainer.ResultStatus.THE_USER_DOES_NOT_EXIST);
		}
		//?????????????????????:null??????????????????
		if(CommonUtil.isNull(entity.getPasswordExpiration()) || new Date().getTime() > entity.getPasswordExpiration().getTime()){
			throw new CustomerException("?????????????????????????????????????????????", CommonEnumContainer.ResultStatus.TOKEN_IS_INVALID);
		}
        //??????????????????
        String secPassword = admUserService.encryPassword(entity, req.getPassword());
        if (!CommonUtil.isEqual(secPassword, entity.getPassword())) {
            comServiceFrequentCache.setToServer(DictionaryKey.MemServiceKeyType.NUMBER_OF_BACKGROUND_USER_LOGIN_ATTEMPTS, req.getUserName());
            throw new CustomerException("??????????????????????????????????????????[????????????" + left + "???].", CommonEnumContainer.ResultStatus.ABNORMAL_OPERATION);
        }

        //?????????????????????????????????
        comServiceFrequentCache.removeFromServer(DictionaryKey.MemServiceKeyType.NUMBER_OF_BACKGROUND_USER_LOGIN_ATTEMPTS, req.getUserName());
        //??????????????????????????????????????????
        commonServiceCache.getInstance(DictionaryKey.Keys.BACKGROUND_USER_TOKEN, CacheSerializableDelegate.jsonSeriaze()).removeFromServer(admUserService.encryToken(entity));


        UserStorage storage = new UserStorage();
        storage.setId(entity.getId());
        storage.setName(entity.getUserName());
        //????????????
        storage.setShowName(entity.getNickName());
        storage.setPermissions(admUserService.getTotalPermissions(entity));

        //???????????? ??????tokenKey

        String newTokenKey = GUIDGenerator.getGUID();
        TbAdmUser upAdmin = new TbAdmUser();
        upAdmin.setId(entity.getId());
        upAdmin.setAuthToken(newTokenKey);
        upAdmin.setLastLoginDate(new Date());
		admUserService.updateSelectiveByPrimaryKey(upAdmin);
        storage.setAuthToken(admUserService.encryToken(entity));
        if (CommonUtil.isEqual(req.getLoginMode().getValue(), CommonEnumContainer.UserLoginMode.USERNAME_PASSWORD.getValue())) {
            //save to cache
            Integer time = req.getStoreTime();
            if (CommonUtil.isNull(time)) {
                time = Integer.parseInt(dictionaryCache.getValue(DictionaryKey.Keys.BACKGROUND_USER_TOKEN.getValue()));
            }
            time *= 60 * 60;
            commonServiceCache.getInstance(DictionaryKey.Keys.BACKGROUND_USER_TOKEN, CacheSerializableDelegate.jsonSeriaze()).setToServer(storage.getAuthToken(), storage, time);
        }
        return storage;
    }

    public void logout(HttpServletRequest request) {
        Cookie cookie = CookieHelper.getCookieByName(request, DictionaryKey.Keys.BACKGROUND_USER_TOKEN.getValue());
        if (CommonUtil.isNull(cookie)) {
            return;
        }
        commonServiceCache.getInstance(DictionaryKey.Keys.BACKGROUND_USER_TOKEN, CacheSerializableDelegate.jsonSeriaze()).removeFromServer(cookie.getValue());
    }

    /**
     * ????????????????????????????????????
     *
     * @return UserStorage ??????????????????
     */
    public UserStorage getAdminByServerStorage(HttpServletRequest request){
        Cookie cookie = CookieHelper.getCookieByName(request, DictionaryKey.Keys.BACKGROUND_USER_TOKEN.getValue());
        String token = null;
        if (CommonUtil.isNotNull(cookie)) {
            token = cookie.getValue();
        }
        CommonServiceCache adminCache = commonServiceCache.getInstance(DictionaryKey.Keys.BACKGROUND_USER_TOKEN, CacheSerializableDelegate.jsonSeriaze());
        return adminCache.getFromServer(token, UserStorage.class);
    }

    public boolean hasPermission(String actionPath, HttpServletRequest request) throws Exception {
        return hasPermission(actionPath, request, false);
    }

    public boolean hasPermission(String actionPath, HttpServletRequest request, boolean logRightId) throws Exception {
        UserStorage storage = getAdminByServerStorage(request);
        if (CommonUtil.isNull(storage)) {
            throw new NoLoginException();
        }
        boolean hasAuth = false;
        Set<Long> sets = admColumnService.hashAuth(actionPath);
        if (CommonUtil.isNull(sets)) {
            return false;
        }
        Iterator<Long> it = sets.iterator();
        while (it.hasNext()) {
            hasAuth = admColumnService.hasColumnPermission(storage, it.next());
            if (hasAuth) {
                break;
            }
        }
        return hasAuth;
    }

    /**
     * ???????????????url??????????????????????????????????????? spring mvc????????????????????????url???jsonString???
     * ??????????????????????????????
     *
     * @param pJoinPoint ?????????
     * @return ???????????????????????????????????????????????????????????????
     */
    @Around(value = "controllerAspect()", argNames = "pJoinPoint")
    public Object checkPermission(ProceedingJoinPoint pJoinPoint) throws Throwable {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = (HttpServletRequest) attributes.resolveReference(RequestAttributes.REFERENCE_REQUEST);
        String actionPath = request.getServletPath();
        //?????? actionPath??????????????????url
        Long nonAuthId = admColumnService.hashNonAuth(actionPath);
        if (CommonUtil.isNotNull(nonAuthId)) {//?????????
            return pJoinPoint.proceed();
        }
        boolean hap = hasPermission(actionPath, request, true);
        if (!hap) {
            throw new NoPermissionException();
        }
        //?????????????????????
        //?????????????????????????????????
        return pJoinPoint.proceed();
    }




}

package com.rong.assembly.api.service.impl;

import com.rong.assembly.api.service.SmsHelper;
import com.rong.assembly.api.service.TripartiteMessageService;
import com.rong.cache.foreign.SMSVerifyCodeFrequent;
import com.rong.cache.service.CommonServiceCache;
import com.rong.cache.service.DictionaryCache;
import com.rong.cache.service.SMSVerifyFrequentCache;
import com.rong.common.consts.CommonEnumContainer;
import com.rong.common.consts.DictionaryKey;
import com.rong.common.exception.CustomerException;
import com.rong.common.exception.NoExistsException;
import com.rong.common.module.Result;
import com.rong.common.util.CommonUtil;
import com.rong.common.util.DateUtil;
import com.rong.common.util.EnumHelperUtil;
import com.rong.common.util.NumberUtil;
import com.rong.common.util.StringUtil;
import com.rong.member.mapper.MemBaseMapper;
import com.rong.member.module.query.TsMemBase;
import com.rong.member.module.req.TqCheckVerificationCode;
import com.rong.member.module.req.TqGetVerificationCode;
import com.rong.member.module.resp.TmGetVerificationCode;
import com.rong.user.mapper.MessageHistoryMapper;
import com.rong.user.module.entity.TbMessageHistory;
import com.vitily.mybatis.core.wrapper.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@Slf4j
public class TripartiteMessageServiceImpl implements TripartiteMessageService {

    private final MemBaseMapper memBaseMapper;
    private final DictionaryCache dictionaryCache;
    private final SMSVerifyFrequentCache smsVerifyFrequentCache;
    private final CommonServiceCache commonServiceCache;
    @Autowired
    private MessageHistoryMapper messageHistoryMapper;
    @Autowired
    SmsHelper smsHelper;

    @Autowired
    public TripartiteMessageServiceImpl(MemBaseMapper memBaseMapper, DictionaryCache dictionaryCache, SMSVerifyFrequentCache smsVerifyFrequentCache, CommonServiceCache commonServiceCache) {
        this.memBaseMapper = memBaseMapper;
        this.dictionaryCache = dictionaryCache;
        this.smsVerifyFrequentCache = smsVerifyFrequentCache;
        this.commonServiceCache = commonServiceCache;
    }

    /**
     * ???????????????
     */
    @Override
    public TmGetVerificationCode getVerificationCode(TqGetVerificationCode req) {
        if (CommonUtil.isNull(req.getContentType())) {
            req.setContentType(CommonEnumContainer.TripartiteMessageContentType.REGISTER.getValue());
        }
        //????????????
        checkVerificationCodeService(req);
        //??????????????? todo ?????????????????????????????????
        String verificationCode = StringUtil.getRandomNumber(4);

        //??????
        CommonEnumContainer.TripartiteMessageContentType contentType = EnumHelperUtil.getByValue(CommonEnumContainer.TripartiteMessageContentType.class, req.getContentType());
        sendVerificationCode(req.getReceiver(),
                contentType, EnumHelperUtil.getByValue(CommonEnumContainer.TripartiteMessageCodeType.class, req.getCodeType()),
                verificationCode
        );


        //????????????????????????
        TmGetVerificationCode resp = new TmGetVerificationCode();
        if (CommonUtil.booleanOf(dictionaryCache.getValue(DictionaryKey.Keys.WHETHER_THE_MESSAGE_IS_DISPLAYED_AT_THE_FRONT_DESK.getValue()))) {
            resp.setContent(verificationCode);
        } else {
            resp.setContent("???????????????????????????????????????");
            log.warn("send verifycode:" + verificationCode);
        }
        Integer seconds = NumberUtil.toInteger(dictionaryCache.getValue(DictionaryKey.Keys.SEND_CAPTCHA_TIME_INTERVAL.getValue()));
        resp.setSeconds(seconds);//
        return resp;
    }

    @Override
    public Result isLegalVerificationCode(TqCheckVerificationCode req) {

        String cacheKey = req.getReceiver() + "_" + req.getContentType();
        SMSVerifyCodeFrequent exitsEntity = smsVerifyFrequentCache.getFromServer(cacheKey, SMSVerifyCodeFrequent.class);
        if (CommonUtil.isNull(exitsEntity)) {
            return Result.miss(CommonEnumContainer.ResultStatus.ABNORMAL_OPERATION,"??????????????????");
        }

        //????????????????????????????????????????????????
        Integer maxCodeErrorCount = NumberUtil.toInteger(dictionaryCache.getValue(DictionaryKey.Keys.NUMBER_OF_CAPTCHA_ATTEMPTS_PER_DAY.getValue()));

        if (maxCodeErrorCount != null && maxCodeErrorCount <= exitsEntity.getErrorCount()) {
            return Result.miss(CommonEnumContainer.ResultStatus.ABNORMAL_OPERATION,"?????????????????????????????????????????????????????????????????????");
        }
        if (!CommonUtil.isEqual(req.getVerificationCode(), exitsEntity.getContent())) {
            smsVerifyFrequentCache.upErrorCount(cacheKey);
            return Result.miss(CommonEnumContainer.ResultStatus.ABNORMAL_OPERATION,"???????????????????????????????????????");
        }
        //??????????????????

        Integer lastSendSMSTime = NumberUtil.toInteger(dictionaryCache.getValue(DictionaryKey.Keys.SEND_CAPTCHA_TIME_INTERVAL.getValue()));
        Long restMils = exitsEntity.getMillTime() + lastSendSMSTime * 1000 - System.currentTimeMillis();
        if (restMils <= 0) {
            return Result.miss(CommonEnumContainer.ResultStatus.ABNORMAL_OPERATION,"???????????????????????????????????????",false);
        }
        return Result.success();
    }

    @Override
    public Result<Boolean> removeVerificationCode(TqCheckVerificationCode req) {
        String cacheKey = req.getReceiver() + "_" + req.getContentType();
        smsVerifyFrequentCache.removeFromServer(cacheKey);
        return Result.success();
    }

    /**
     * ?????????????????????????????????
     *
     * @param req
     * @
     */
    private void checkVerificationCodeService(TqGetVerificationCode req) {
        CommonEnumContainer.TripartiteMessageContentType contentType = EnumHelperUtil.getByValue(CommonEnumContainer.TripartiteMessageContentType.class, req.getContentType());
        switch (contentType) {
            case REGISTER://???????????????????????????     ????????????????????????????????????????????????????????????
            {
                // todo ??????????????????????????? 2020???1???17??? 17:58:45
//                if(memBaseMapper.selectOne(new QueryWrapper<TbMemBase>()
//                        .eq(TsMemBase.Fields.userName,req.getReceiver())
//                        .select(TsMemBase.Fields.id)
//                ) != null){
//                    throw new CustomerException("???????????????????????????????????????????????????", CommonEnumContainer.ResultStatus.?????????);
//                }
                break;
            }
            case CANCEL_MOBILE_PHONE_NUMBER://????????????????????????????????????
            case AUTHENTICATION:// ????????????:////??????????????????????????????  ?????? ????????????
            {
                break;
            }
            case RETRIEVE_PASSWORD:////
            {
                if(memBaseMapper.selectOne(new QueryWrapper()
                        .eq(TsMemBase.Fields.userName,req.getReceiver())
                        .select(TsMemBase.Fields.id)
                ) == null){
                    throw new NoExistsException("????????????????????????????????????.");
                }
                break;
            }

            default:
                break;
        }
    }

    /**
     * @param receiver    receiver
     * @param contentType ????????????
     * @param content     ??????
     * @return
     */
    private void sendVerificationCode(String receiver, CommonEnumContainer.TripartiteMessageContentType contentType, CommonEnumContainer.TripartiteMessageCodeType codeType, String content) {
        //????????????????????????
        String cacheKey = receiver + "_" + contentType.getValue();
        SMSVerifyCodeFrequent exitsEntity = smsVerifyFrequentCache.getFromServer(cacheKey, SMSVerifyCodeFrequent.class);
        if (exitsEntity != null) {
            //?????????????????????????????????????????????????????????????????????????????????
            Integer maxCodeErrorCount = NumberUtil.toInteger(dictionaryCache.getValue(DictionaryKey.Keys.NUMBER_OF_CAPTCHA_ATTEMPTS_PER_DAY.getValue()));
            if (maxCodeErrorCount != null && maxCodeErrorCount <= exitsEntity.getErrorCount()) {
                Long restMils = exitsEntity.getMillTime() - System.currentTimeMillis() + 86400000;
                throw new CustomerException("????????????????????????????????????????????? " + DateUtil.getRestTime(restMils) + "????????????", CommonEnumContainer.ResultStatus.PAGE_REQUEST_EXCEPTION);
            }

            //?????????????????????????????????
            //???????????????????????????
            Integer maxSendSMSCount = NumberUtil.toInteger(dictionaryCache.getValue(DictionaryKey.Keys.MEMBERS_CAN_SEND_SMS_NUMBER_OF_TIMES_PER_DAY.getValue()));
            // null ????????????5???
            if (maxSendSMSCount != null && maxSendSMSCount <= exitsEntity.getCount()) {
                Long restMils = exitsEntity.getMillTime() - System.currentTimeMillis() + 86400000;
                throw new CustomerException("?????????????????????????????????????????? " + DateUtil.getRestTime(restMils) + "????????????", CommonEnumContainer.ResultStatus.PAGE_REQUEST_EXCEPTION);
            }

            //???????????????????????????????????????
            Integer lastSendSMSTime = NumberUtil.toInteger(dictionaryCache.getValue(DictionaryKey.Keys.SEND_CAPTCHA_TIME_INTERVAL.getValue()));
            Long restMils = exitsEntity.getMillTime() + lastSendSMSTime * 1000 - System.currentTimeMillis();
            if (restMils >= 0) {
                //????????????????????????????????????
                //restMils???(1000*60)
                throw new CustomerException("????????? " + DateUtil.getRestTime(restMils) + " ????????????????????????", CommonEnumContainer.ResultStatus.PAGE_REQUEST_EXCEPTION);
            }
        }

        //????????????
        sendMessage(receiver, contentType, codeType, content, dictionaryCache);
        smsVerifyFrequentCache.setToServer(cacheKey, content);
    }

    /**
     * ????????????
     *
     * @param receiver
     * @param contentType ???????????????
     * @param codeType    ??? ????????????
     * @param content
     * @return
     * @
     */
    private void sendMessage(String receiver, CommonEnumContainer.TripartiteMessageContentType contentType, CommonEnumContainer.TripartiteMessageCodeType codeType, String content, DictionaryCache dictionaryCache){
        //????????????
        //???????????????
        TbMessageHistory history = new TbMessageHistory();
        history.setCodeType(codeType.getValue());
        history.setContentType(contentType.getValue());
        history.setContent(content);
        history.setDeltag(false);
        history.setCreateDate(new Date());
        history.setTarget(receiver);
        //??????????????????
        Integer isSend = NumberUtil.toInteger(dictionaryCache.getValue(DictionaryKey.Keys.WHETHER_IT_IS_TRUE_TO_SEND_SHORT_MESSAGES.getValue()));
        if (!CommonUtil.isEqual(isSend, 1)) {
            history.setState(-1);
            messageHistoryMapper.insert(history);
            return;//????????????????????????
        }
        CustomerException e = null;
        switch (codeType) {
            case SMS_TEXT:
            case SMS_VOICE:
            case SMS_GRAPHIC_CODE:
                switch (contentType) {
                    case REGISTER:
                    case RETRIEVE_PASSWORD:
                        Result result = smsHelper.send(contentType, receiver, content);
                        if (!result.isSucceed()) {
                            e = new CustomerException(result);
                        }
                        history.setState(result.isSucceed() ? CommonEnumContainer.State.NORMAL.getValue(): CommonEnumContainer.State.INVALID.getValue());
                        break;
                    case VERIFY_PHONE_NUMBER:
                        break;
                    default:
                        break;
                }
                break;
            case EMAIL_TEXT:
            case EMAIL_VOICE:
            case EMAIL_GRAPHICS_CODE:
                switch (contentType) {
                    case REGISTER:
                        //sendState = emailImpl.sendRegisterCode(receiver, content);
                        break;
                    case RETRIEVE_PASSWORD:
                        //sendState = emailImpl.sendFindPasswordCode(receiver, content);
                        break;
                    default:
                        break;
                }
                break;
            default:
                break;
        }
        messageHistoryMapper.insert(history);
        if(e != null){
            throw e;
        }
    }

}
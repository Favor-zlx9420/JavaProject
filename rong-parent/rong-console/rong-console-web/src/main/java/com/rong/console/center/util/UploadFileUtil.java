package com.rong.console.center.util;

import com.rong.cache.base.ViyBasicCache;
import com.rong.cache.service.DictionaryCache;
import com.rong.common.consts.CommonEnumContainer;
import com.rong.common.consts.DictionaryKey;
import com.rong.common.exception.CustomerException;
import com.rong.common.util.CommonUtil;
import com.rong.common.util.DateUtil;
import com.rong.common.util.JSONUtil;
import com.rong.common.util.StringUtil;
import com.rong.console.center.module.request.TqCheckFile;
import com.rong.console.center.module.request.TqUploadShareFile;
import com.rong.console.center.module.response.TrCheckFile;
import com.rong.sys.consts.SysEnumContainer;
import com.rong.sys.module.entity.TbImageSams;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * creator : whh-lether
 * date    : 2019/3/25 15:54
 * desc    :
 **/
public class UploadFileUtil {
    public static TbImageSams kindEditorUpload(
                                       String subff,
                                       DictionaryCache dictionaryCache,
                                       ViyBasicCache viyBasicCache,
                                       MultipartFile imgFile,
                                       HttpServletRequest request,
                                       HttpServletResponse response,
                                       String dirName)throws Exception{
        return kindEditorUploadOfName(subff,dictionaryCache,viyBasicCache,imgFile,request,response,dirName,
                DateUtil.getCurDateTime(DateUtil.yyyyMMdd_EN),
                DateUtil.getCurDateTime(DateUtil.yyyyMMddHHmmss_EN)+ CommonUtil.ranStr(8),
                true,
                CommonUtil.isEqual(request.getParameter("resize"), "1")
        );
    }

    public static TbImageSams kindEditorUploadOfName(
            String suffix,
            DictionaryCache dictionaryCache,
            ViyBasicCache viyBasicCache,
            MultipartFile imgFile, HttpServletRequest request, HttpServletResponse response, String dirName, String path, String saveFileName,
            boolean pushPic,
            boolean resize)throws Exception{
        boolean isFile = !CommonUtil.isEqual(dirName, "image");
        if(isFile){
            Integer maxSize = Integer.parseInt(dictionaryCache.getValue(DictionaryKey.Keys.ATTACHMENT_UPLOAD_SIZE_LIMIT.getValue()))*1024;
            if(imgFile.getSize() >= maxSize){
                throw new CustomerException("??????????????????"+maxSize/1024+"k", CommonEnumContainer.ResultStatus.THE_PARAMETERS_DO_NOT_MEET_THE_REQUIREMENTS);
            }
        }else{
            Integer maxSize = Integer.parseInt(dictionaryCache.getValue(DictionaryKey.Keys.IMAGE_UPLOAD_SIZE_LIMIT.getValue()))*1024;
            if(imgFile.getSize() >= maxSize){
                throw new CustomerException("??????????????????"+maxSize/1024+"k", CommonEnumContainer.ResultStatus.THE_PARAMETERS_DO_NOT_MEET_THE_REQUIREMENTS);
            }
        }
        // ????????????
        String savePath=dictionaryCache.getValue(DictionaryKey.Keys.FILE_UPLOAD_ADDRESS.getValue());
        if(StringUtil.isEmpty(savePath)){
            throw new CustomerException("???????????????????????????", CommonEnumContainer.ResultStatus.ABNORMAL_OPERATION);
        }
        //????????????????????????
        savePath += "/fs/uf/";
        //??????????????????URL
        String port = ":" + request.getServerPort();
        if(CommonUtil.isEqual(port, ":80")){
            port = "";
        }
        String saveUrl = dictionaryCache.getValue(DictionaryKey.Keys.FILE_SERVER_DOMAIN_NAME.getValue()) + "/fs/uf/";
        //????????????????????????????????????
        HashMap<String, String> extMap = new HashMap <>();
        extMap.put("image", "gif,jpg,jpeg,png,bmp");
        extMap.put("file", "doc,docx,xls,xlsx,ppt,htm,html,txt,zip,rar,gz,bz2,pdf,swf,flv,mp3,wav,mid,avi,mpg,mp4,rm");
        dirName = !CommonUtil.isEqual(dirName, "image") ? "file" : "image";
        if(!extMap.containsKey(dirName)){
            throw new CustomerException("??????????????????", CommonEnumContainer.ResultStatus.THE_PARAMETERS_DO_NOT_MEET_THE_REQUIREMENTS);
        }
        int sysType = SysEnumContainer.SysType.PICTORIAL_INFORMATION.getValue();
        if(!CommonUtil.isEqual(dirName, "image")){
            sysType = SysEnumContainer.SysType.FILE_CLASS.getValue();
        }
        // ??????????????????
        String realFileName = imgFile.getOriginalFilename();
        String extName=realFileName.substring(realFileName.lastIndexOf(".")+1);
        if(!Arrays.asList(extMap.get(dirName).split(",")).contains(extName)){
            throw new CustomerException("?????????????????????["+extName+"]???????????????????????????\n?????????" + extMap.get(dirName) + "??????", CommonEnumContainer.ResultStatus.THE_PARAMETERS_DO_NOT_MEET_THE_REQUIREMENTS);
        }
        //??????????????????

        //????????????
        File uploadDir = new File(savePath);
        if(!uploadDir.isDirectory()){
            uploadDir.mkdirs();
        }
        //?????????????????????
        if(!uploadDir.canWrite()){
            throw new CustomerException("???????????????????????????", CommonEnumContainer.ResultStatus.WITHOUT_PERMISSION);
        }
        //???????????????
        savePath += dirName + "/";
        saveUrl += dirName + "/";
        savePath += path + "/";
        saveUrl += path + "/";

        File dirFile = new File(savePath);
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }

        String newRealFileName = saveFileName +"." + extName;//???????????????????????????
        File uploadFile = new File(savePath + newRealFileName);
        Path _path = Paths.get(savePath + newRealFileName);
        String fileType = Files.probeContentType(_path);
        //??????????????????????????????????????????************************************

        //??????????????????????????????????????????************************************
        FileCopyUtils.copy(imgFile.getBytes(), uploadFile);

        //?????????????????????type != null????????????
        String limitName = newRealFileName;//???:???????????????
        String cName = newRealFileName;//???:???????????????
        String bName = newRealFileName;//???:???????????????
        //String dF = originalName + "_detail" + extName;//????????????
        //String aF = originalName + "_album" + extName;//?????????
        //String lF = originalName + "_list" + extName;//?????????

        if(resize){
            //????????????
            limitName = saveFileName+"_lmt."+extName;//???
            cName = saveFileName+"_cnt."+extName;//???
            bName = saveFileName+"_big."+extName;//???
            ImageUtils.resize(savePath + newRealFileName, savePath+limitName, 108, 108);
            ImageUtils.resize(savePath + newRealFileName, savePath+cName, 200, 200);
            ImageUtils.resize(savePath + newRealFileName, savePath+bName, 400, 400);
            //PC
            ImageUtils.resize(savePath + newRealFileName, savePath+saveFileName+"_pc."+extName, 230, 520);
            //mobile
            ImageUtils.resize(savePath + newRealFileName, savePath+saveFileName+"_mobile."+extName, 150, 400);
        }
        TbImageSams newSam=new TbImageSams();

        //????????????
        newSam.setLmtSrc(saveUrl+limitName);
        newSam.setBigSrc(saveUrl + bName);
        newSam.setCntSrc(saveUrl + cName);
        newSam.setType(sysType);
        newSam.setName(request.getParameter("name"));
        if(!CommonUtil.isNull(newSam.getName())){
            //newSam.setName(new String(newSam.getName().getBytes("ISO-8859-1"),"UTF-8"));
        }
        String labelIds = request.getParameter("lbIds");
        if(CommonUtil.isNumOrD(labelIds)){
            newSam.setLabelIds(labelIds);
        }
        if(pushPic){
            viyBasicCache.publishMulToOne(DictionaryKey.ViyCacheSubstrTopic.PICTURE_COLLECTOR.getValue()+"-"+suffix, JSONUtil.toJSONString(newSam));
        }
        newSam.setLmtSrc(newSam.getLmtSrc() + "?v=" + System.currentTimeMillis());
        newSam.setBigSrc(newSam.getBigSrc() + "?v=" + System.currentTimeMillis());
        newSam.setCntSrc(newSam.getCntSrc() + "?v=" + System.currentTimeMillis());
        return newSam;
    }



    /**
     * ???????????????????????????
     * @param dictionaryCache
     * @param req
     * @return
     */
    public static String getSaveFileName(DictionaryCache dictionaryCache, TqCheckFile req){
        String savePath=dictionaryCache.getValue(DictionaryKey.Keys.FILE_UPLOAD_ADDRESS.getValue()) + "/fs/uf/";
        savePath += req.getSavePath() + "/";
        savePath += req.getServiceType() + "/";
        File dirFile = new File(savePath);
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }
        return savePath + req.getUserId() + "-"+ req.getFileMd5() + "." + req.getExt();
    }

    /**
     * ?????????????????????????????????
     * @param dictionaryCache
     * @param req
     * @return
     */
    public static File getSaveTempPath(DictionaryCache dictionaryCache, TqCheckFile req){
        String savePath=dictionaryCache.getValue(DictionaryKey.Keys.FILE_UPLOAD_ADDRESS.getValue()) + "/fs/uf/";
        savePath += req.getSavePath() + "/";
        savePath += req.getServiceType() + "/";
        savePath += req.getUserId() + "-"+ req.getFileMd5() + "-tmp/";
        return new File(savePath);
    }

    /**
     * ????????????????????????????????????
     * @param dictionaryCache
     * @param req
     * @return
     */
    public static String getSaveFileUrl(DictionaryCache dictionaryCache, TqCheckFile req){
        String saveUrl = dictionaryCache.getValue(DictionaryKey.Keys.FILE_SERVER_DOMAIN_NAME.getValue()) + "/fs/uf/";
        saveUrl += req.getSavePath() + "/";
        saveUrl += req.getServiceType() + "/";
        return saveUrl + req.getUserId() + "-"+ req.getFileMd5() + "." + req.getExt();
    }

    /**
     * ??????????????????????????????
     * ?????????????????????????????????????????????type????????????????????? (userId-fileMd5).ext???
     * ?????????????????????????????????????????????????????????????????????????????????????????????userId-fileMd5-tmp????????????????????????????????????????????????????????????????????????????????????
     * ???????????????????????????
     * @param req ??????+??????md5???
     * @return ?????????????????? url????????????????????? null
     */
    public static TrCheckFile exitsFile(DictionaryCache dictionaryCache, TqCheckFile req){
        TrCheckFile resp = new TrCheckFile();
        File f = new File(getSaveFileName(dictionaryCache,req));
        resp.setExists(f.exists());
        resp.setFileUrl(getSaveFileUrl(dictionaryCache,req));
        if(!f.exists()){
            //?????????????????????
            List hadChunks = new ArrayList();
            File tmpPath = getSaveTempPath(dictionaryCache,req);
            if(tmpPath.exists()){
                hadChunks.addAll(Arrays.asList(Objects.requireNonNull(tmpPath.list())));
            }
            resp.setHadChunks(hadChunks);
        }
        return resp;
    }


    public static TrCheckFile shareUpload(DictionaryCache dictionaryCache, TqUploadShareFile req)throws IOException {

        //?????????????????????????????????????????????
        TrCheckFile resp = exitsFile(dictionaryCache,req);
        if(resp.isExists()){
            return resp;
        }
        //?????????????????????????????????
        if(null == req.getChunk()){
            FileCopyUtils.copy(req.getFile().getBytes(), new File(getSaveFileName(dictionaryCache,req)));
            resp.setExists(true);
            return resp;
        }
        File tmpPath = getSaveTempPath(dictionaryCache,req);
        if (!tmpPath.exists()) {
            tmpPath.mkdirs();
        }
        //???????????????????????????????????????????????????????????????
        File tf = new File(tmpPath.getPath() + "/" + req.getChunk());
        if(tf.exists()){
            return resp;
        }
        FileCopyUtils.copy(req.getFile().getBytes(), tf);
        String[] shareFiles = tmpPath.list();
        if(shareFiles.length == req.getChunks()){
            //??????
            if(!mergeFiles(tmpPath,getSaveFileName(dictionaryCache,req))){
                throw new CustomerException("?????????????????????", CommonEnumContainer.ResultStatus.ABNORMAL_OPERATION);
            }
            resp.setExists(true);
            resp.setHadChunks(Arrays.asList(Objects.requireNonNull(shareFiles)));
        }


        return resp;
    }


    private static boolean mergeFiles(File tmpFile, String resultPath) {
        File[] files = tmpFile.listFiles();
        if (files == null || files.length < 1 || StringUtil.isEmpty(resultPath)) {
            return false;
        }
        if (files.length == 1) {
            return files[0].renameTo(new File(resultPath));
        }

        File resultFile = new File(resultPath);
        //??????
        Arrays.sort(files, Comparator.comparing(o -> Integer.valueOf(o.getName())));
        try {
            FileChannel resultFileChannel = new FileOutputStream(resultFile, true).getChannel();
            for (int i = 0; i < files.length; i ++) {
                FileChannel blk = new FileInputStream(files[i]).getChannel();
                resultFileChannel.transferFrom(blk, resultFileChannel.size(), blk.size());
                blk.close();
            }
            resultFileChannel.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        for(File f:files){
            f.delete();
        }
        tmpFile.delete();

        return true;
    }


}

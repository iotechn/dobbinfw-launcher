package com.dobbinsoft.fw.launcher.controller;

import cn.hutool.core.io.file.FileNameUtil;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.model.MatchMode;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PolicyConditions;
import com.aliyun.oss.model.PutObjectRequest;
import com.dobbinsoft.fw.core.Const;
import com.dobbinsoft.fw.core.util.GeneratorUtil;
import lombok.extern.log4j.Log4j2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.sql.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by rize on 2019/2/13.
 */
@Log4j2
@Controller
@RequestMapping("/upload")
public class FileUploadController {

    @Autowired
    private StringRedisTemplate userRedisTemplate;

//    @Autowired
//    private  storageClient;


    /**
     * 后台通过服务器间接传文件
     *
     * @param file
     * @return
     * @throws IOException
     */
    @PostMapping("/admin")
    @ResponseBody
    public Object create(@RequestParam("file") MultipartFile file, HttpServletRequest request) throws IOException {
        String accessToken = request.getHeader(Const.ADMIN_ACCESS_TOKEN);
        String json = userRedisTemplate.opsForValue().get(Const.ADMIN_REDIS_PREFIX + accessToken);
        if (!StringUtils.isEmpty(json)) {
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(file.getSize());
            objectMetadata.setContentType(file.getContentType());
            String ext = FileNameUtil.getSuffix(file.getOriginalFilename());
            String uuid = GeneratorUtil.genFileName();
            Map<String, Object> data = new HashMap<>();
//            data.put("url", unimallAliOSSProperties.getBaseUrl() + unimallAliOSSProperties.getDir() + uuid + "." + ext);
            data.put("errno", 200);
            data.put("errmsg", "成功");
            return data;
        }
        throw new RuntimeException("权限不足");
    }

    /**
     * 上传文件到文件系统
     *
     * @param file
     * @param fsf  FileSystem File 上传覆盖具体某个文件
     * @return
     */
    @PostMapping("/local")
    @ResponseBody
    public Object local(@RequestParam("file") MultipartFile file, String fsf, HttpServletRequest request) throws IOException {
        String accessToken = request.getHeader(Const.ADMIN_ACCESS_TOKEN);
        String json = userRedisTemplate.opsForValue().get(Const.ADMIN_REDIS_PREFIX + accessToken);
        if (!StringUtils.isEmpty(json)) {
            int i = fsf.lastIndexOf("/");
            if (i > 0) {
                String substring = fsf.substring(0, i);
                File dir = new File(substring);
                if (!dir.exists()) {
                    dir.mkdir();
                }
                File fsFile = new File(fsf);
                if (fsFile.exists()) {
                    fsFile.delete();
                }
                InputStream fis = file.getInputStream();
                OutputStream os = new FileOutputStream(fsf);
                FileCopyUtils.copy(fis, os);
                fis.close();
                os.close();
                Map<String, Object> data = new HashMap<>();
                data.put("errno", 200);
                data.put("errmsg", "成功");
                return data;
            } else {
                throw new RuntimeException("文件系统路径不正确");
            }
        }
        throw new RuntimeException("权限不足");
    }


    /**
     * Post请求
     */
    @RequestMapping(method = RequestMethod.POST)
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Map<String, String[]> parameterMap = request.getParameterMap();
        JSONObject responseJson = new JSONObject();
        responseJson.put("code", 200);
        for (String key : parameterMap.keySet()) {
            responseJson.put(key, parameterMap.get(key)[0]);
        }
        response(request, response, responseJson.toJSONString(), HttpServletResponse.SC_OK);
    }

    /**
     * 服务器响应结果
     *
     * @param request
     * @param response
     * @param results
     * @param status
     * @throws IOException
     */
    private void response(HttpServletRequest request, HttpServletResponse response, String results, int status)
            throws IOException {
        String callbackFunName = request.getParameter("callback");
        response.addHeader("Content-Length", String.valueOf(results.length()));
        if (callbackFunName == null || callbackFunName.equalsIgnoreCase(""))
            response.getWriter().println(results);
        else
            response.getWriter().println(callbackFunName + "( " + results + " )");
        response.setStatus(status);
        response.flushBuffer();
    }

    /**
     * 服务器响应结果
     */
    private void response(HttpServletRequest request, HttpServletResponse response, String results) throws IOException {
        String callbackFunName = request.getParameter("callback");
        if (callbackFunName == null || callbackFunName.equalsIgnoreCase(""))
            response.getWriter().println(results);
        else
            response.getWriter().println(callbackFunName + "( " + results + " )");
        response.setStatus(HttpServletResponse.SC_OK);
        response.flushBuffer();
    }

}

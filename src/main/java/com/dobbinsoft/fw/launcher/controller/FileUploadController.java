package com.dobbinsoft.fw.launcher.controller;

import cn.hutool.core.io.file.FileNameUtil;
import com.alibaba.fastjson.JSONObject;
import com.dobbinsoft.fw.core.Const;
import com.dobbinsoft.fw.core.entiy.inter.IdentityOwner;
import com.dobbinsoft.fw.core.entiy.inter.PermissionOwner;
import com.dobbinsoft.fw.core.util.GeneratorUtil;
import com.dobbinsoft.fw.launcher.inter.AfterFileUpload;
import com.dobbinsoft.fw.launcher.inter.BeforeFileUpload;
import com.dobbinsoft.fw.launcher.inter.BeforeFileUploadPath;
import com.dobbinsoft.fw.launcher.permission.IAdminAuthenticator;
import com.dobbinsoft.fw.launcher.permission.IUserAuthenticator;
import com.dobbinsoft.fw.support.storage.StorageClient;
import com.dobbinsoft.fw.support.storage.StoragePrivateResult;
import com.dobbinsoft.fw.support.storage.StorageRequest;
import com.dobbinsoft.fw.support.storage.StorageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by rize on 2019/2/13.
 */
@Controller
@RequestMapping("/upload")
public class FileUploadController {

    @Autowired
    private IUserAuthenticator userAuthenticator;

    @Autowired
    private StorageClient storageClient;

    @Autowired
    private IAdminAuthenticator adminAuthenticator;

    @Autowired(required = false)
    private AfterFileUpload afterFileUpload;

    @Autowired(required = false)
    private BeforeFileUpload beforeFileUpload;

    @Autowired(required = false)
    private BeforeFileUploadPath beforeFileUploadPath;

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);


    /**
     * ????????????????????????????????????
     *
     * @param file
     * @return
     * @throws IOException
     */
    @PostMapping("/admin")
    @ResponseBody
    public Object createAdmin(@RequestParam("file") MultipartFile file, HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (this.beforeFileUpload != null) {
            this.beforeFileUpload.before(request);
        }
        String accessToken = request.getHeader(Const.ADMIN_ACCESS_TOKEN);
        PermissionOwner admin = adminAuthenticator.getAdmin(accessToken);
        if (admin != null) {
            return commonsUpload(file);
        }
        throw new RuntimeException("????????????");
    }

    /**
     * ??????????????????????????????????????? ????????????????????????URL???
     *
     * @param file
     * @param request
     * @param response
     * @return
     * @throws IOException
     */
    @PostMapping("/admin/private")
    @ResponseBody
    public Object createAdminPrivate(@RequestParam("file") MultipartFile file, HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (this.beforeFileUpload != null) {
            this.beforeFileUpload.before(request);
        }
        String accessToken = request.getHeader(Const.ADMIN_ACCESS_TOKEN);
        PermissionOwner admin = adminAuthenticator.getAdmin(accessToken);
        if (admin != null) {
            InputStream inputStream = null;
            Map<String, Object> data = new HashMap<>();
            try {
                String ext = FileNameUtil.getSuffix(file.getOriginalFilename());
                String uuid = GeneratorUtil.genFileName();
                StorageRequest storageRequest = new StorageRequest();
                storageRequest.setContentType(file.getContentType());
                storageRequest.setFilename(uuid + "." + ext);
                storageRequest.setSize(file.getSize());
                inputStream = file.getInputStream();
                storageRequest.setIs(inputStream);
                storageRequest.setPath("private");
                StoragePrivateResult result = storageClient.savePrivate(storageRequest);
                if (this.afterFileUpload != null) {
                    this.afterFileUpload.afterPrivate(storageRequest.getFilename(), result.getUrl(), result.getKey(), file.getSize());
                }
                if (result.isSuc()) {
                    data.put("key", result.getKey());
                    data.put("url", result.getUrl());
                    data.put("errno", 200);
                    data.put("errmsg", "??????");
                    return data;
                }
            } catch (IOException e) {
                throw new RuntimeException("????????????");
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }
        }
        throw new RuntimeException("????????????");
    }

    @PostMapping("/user")
    @ResponseBody
    public Object createUser(@RequestParam("file") MultipartFile file, HttpServletRequest request) throws Exception {
        if (this.beforeFileUpload != null) {
            this.beforeFileUpload.before(request);
        }
        String accessToken = request.getHeader(Const.USER_ACCESS_TOKEN);
        IdentityOwner user = userAuthenticator.getUser(accessToken);
        if (user != null) {
            return commonsUpload(file);
        }
        throw new RuntimeException("????????????");
    }

    private Object commonsUpload(MultipartFile file) throws IOException {
        InputStream inputStream = null;
        Map<String, Object> data = new HashMap<>();
        try {
            String ext = FileNameUtil.getSuffix(file.getOriginalFilename());
            String uuid = GeneratorUtil.genFileName();
            StorageRequest storageRequest = new StorageRequest();
            storageRequest.setContentType(file.getContentType());
            storageRequest.setFilename(uuid + "." + ext);
            storageRequest.setSize(file.getSize());
            inputStream = file.getInputStream();
            storageRequest.setIs(inputStream);
            if (this.beforeFileUploadPath != null) {
                storageRequest.setPath(this.beforeFileUploadPath.setPath("commons"));
            } else {
                storageRequest.setPath("commons");
            }
            StorageResult result = storageClient.save(storageRequest);
            if (afterFileUpload != null) {
                afterFileUpload.afterPublic(storageRequest.getFilename(), result.getUrl(), file.getSize());
            }
            if (result.isSuc()) {
                data.put("url", result.getUrl());
                data.put("errno", 200);
                data.put("errmsg", "??????");
            }
        } catch (IOException e) {
            throw new RuntimeException("????????????");
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
        return data;
    }

    /**
     * ???????????????????????????
     *
     * @param file
     * @param fsf  FileSystem File ??????????????????????????????
     * @return
     */
    @PostMapping("/local")
    @ResponseBody
    public Object local(@RequestParam("file") MultipartFile file, String fsf, HttpServletRequest request) throws IOException {
        String accessToken = request.getHeader(Const.ADMIN_ACCESS_TOKEN);
        IdentityOwner user = userAuthenticator.getUser(accessToken);
        if (user != null) {
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
                data.put("errmsg", "??????");
                return data;
            } else {
                throw new RuntimeException("???????????????????????????");
            }
        }
        throw new RuntimeException("????????????");
    }


    /**
     * Post??????
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
     * ?????????????????????
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
     * ?????????????????????
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

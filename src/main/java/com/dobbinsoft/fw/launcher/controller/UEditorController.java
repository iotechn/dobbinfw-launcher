package com.dobbinsoft.fw.launcher.controller;

import cn.hutool.core.io.file.FileNameUtil;
import com.alibaba.fastjson.JSONObject;
import com.dobbinsoft.fw.core.util.GeneratorUtil;
import com.dobbinsoft.fw.launcher.inter.BeforeFileUploadPath;
import com.dobbinsoft.fw.launcher.inter.BeforeUEditorFileUpload;
import com.dobbinsoft.fw.support.image.ImageManager;
import com.dobbinsoft.fw.support.image.ImageModel;
import com.dobbinsoft.fw.support.model.Page;
import com.dobbinsoft.fw.support.storage.StorageClient;
import com.dobbinsoft.fw.support.storage.StorageRequest;
import com.dobbinsoft.fw.support.storage.StorageResult;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 为 UEditor 提供API
 */
@RequestMapping("/ueditor")
@RestController
public class UEditorController {

    @Autowired(required = false)
    private ImageManager imageManager;

    @Autowired(required = false)
    private StorageClient storageClient;

    @Autowired(required = false)
    private BeforeUEditorFileUpload beforeUEditorFileUpload;

    @Autowired(required = false)
    private BeforeFileUploadPath beforeFileUploadPath;

    private static final String SUCCESS = "SUCCESS";

    private static final String FAILED = "FAILED";

    @GetMapping("/cos")
    public String cosGet(String action, String callback, Integer start, Integer size, HttpServletRequest request) throws Exception {
        if (this.beforeUEditorFileUpload != null) {
            this.beforeUEditorFileUpload.before(request);
        }
        String res = "";
        if ("config".equals(action)) {
            // 客户端侧配置
            res = "";
        } else if ("listimage".equals(action)) {
            Page<ImageModel> imageList = imageManager.getImageList(null, null, start / size + 1, size);
            ListPage listPage = new ListPage();
            listPage.setStart(start);
            listPage.setTotal((int) imageList.getTotal());
            listPage.setState(SUCCESS);
            listPage.setList(imageList.getItems().stream().map(item -> {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("url", item.getUrl());
                return jsonObject;
            }).collect(Collectors.toList()));
            res = callback(callback, JSONObject.toJSONString(listPage));
        }
        return callback(callback, res);
    }

    @PostMapping("/cos")
    public String cosPost(String action, MultipartFile upfile, HttpServletRequest request) throws Exception{
        if (this.beforeUEditorFileUpload != null) {
            this.beforeUEditorFileUpload.before(request);
        }
        if ("uploadimage".equals(action)) {
            InputStream inputStream = null;
            try {
                String ext = FileNameUtil.getSuffix(upfile.getOriginalFilename());
                String uuid = GeneratorUtil.genFileName();
                StorageRequest storageRequest = new StorageRequest();
                storageRequest.setContentType(upfile.getContentType());
                storageRequest.setFilename(uuid + "." + ext);
                storageRequest.setSize(upfile.getSize());
                inputStream = upfile.getInputStream();
                storageRequest.setIs(inputStream);
                if (this.beforeFileUploadPath != null) {
                    storageRequest.setPath(this.beforeFileUploadPath.setPath("commons"));
                } else {
                    storageRequest.setPath("commons");
                }
                StorageResult result = storageClient.save(storageRequest);
                if (result.isSuc()) {
                    ImageModel imageModel = new ImageModel();
                    imageModel.setBizType(0);
                    imageModel.setBizTypeTitle("富文本");
                    imageModel.setContentLength(upfile.getSize());
                    imageModel.setBizId(0L);
                    imageModel.setTitle(upfile.getOriginalFilename());
                    imageModel.setUrl(result.getUrl());
                    imageManager.upsertImg(imageModel);
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("state", SUCCESS);
                    jsonObject.put("url", result.getUrl());
                    jsonObject.put("title", upfile.getOriginalFilename());
                    jsonObject.put("original", upfile.getOriginalFilename());
                    return jsonObject.toJSONString();
                }
            } catch (IOException e) {
                throw new RuntimeException("网络错误");
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("state", FAILED);
        return jsonObject.toJSONString();
    }

    private String callback(String callback, String text) {
        return callback + "(" + text + ")";
    }

    @Data
    private static class ListPage {

        private Integer start;

        private Integer total;

        // SUCCESS
        private String state;

        private List<JSONObject> list;

    }

}

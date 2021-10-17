package com.dobbinsoft.fw.launcher.inter;

public interface AfterFileUpload {

    public void afterPublic(String filename, String url, long contentType);

    public void afterPrivate(String filename, String url, String key, long contentType);

}

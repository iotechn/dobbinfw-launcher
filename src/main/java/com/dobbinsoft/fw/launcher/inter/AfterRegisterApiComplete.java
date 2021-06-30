package com.dobbinsoft.fw.launcher.inter;

import com.dobbinsoft.fw.support.model.PermissionPoint;

import java.util.List;

public interface AfterRegisterApiComplete {

    /**
     * 当系统所有API注册完成之后，回调业务系统
     * @param permDTOs
     */
    public void after(List<PermissionPoint> permDTOs);

}

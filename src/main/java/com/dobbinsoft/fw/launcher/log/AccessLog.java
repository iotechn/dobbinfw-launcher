package com.dobbinsoft.fw.launcher.log;

import lombok.Data;

/**
 * ClassName: AccessLog
 * Description: TODO
 *
 * @author: e-weichaozheng
 * @date: 2021-03-17
 */
@Data
public class AccessLog {

    private Long requestId;

    private Long adminId;

    private String group;

    private String method;

}

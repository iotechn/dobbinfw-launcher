package com.dobbinsoft.fw.launcher.exception;


import com.dobbinsoft.fw.core.exception.ServiceExceptionDefinition;

/**
 * Created by rize on 2019/6/30.
 */
public class LauncherExceptionDefinition {

    public static final ServiceExceptionDefinition LAUNCHER_API_REGISTER_FAILED =
            new ServiceExceptionDefinition(9999, "api注册失败");

    public static final ServiceExceptionDefinition LAUNCHER_UNKNOWN_EXCEPTION =
            new ServiceExceptionDefinition(10000, "系统未知异常");

    public static final ServiceExceptionDefinition LAUNCHER_USER_NOT_LOGIN =
            new ServiceExceptionDefinition(10001, "用户尚未登录");

    public static final ServiceExceptionDefinition LAUNCHER_PARAM_CHECK_FAILED =
            new ServiceExceptionDefinition(10002, "参数校验失败");

    public static final ServiceExceptionDefinition LAUNCHER_API_NOT_EXISTS =
            new ServiceExceptionDefinition(10003, "API不存在");

    public static final ServiceExceptionDefinition LAUNCHER_DATA_NOT_CONSISTENT =
            new ServiceExceptionDefinition(10004, "attention please！系统内部数据不一致 注意！！");

    public static final ServiceExceptionDefinition LAUNCHER_API_NOT_SUPPORT =
            new ServiceExceptionDefinition(10005, "Api 不再支持调用");

    public static final ServiceExceptionDefinition LAUNCHER_ADMIN_NOT_LOGIN =
            new ServiceExceptionDefinition(10006, " 管理员尚未登录");

    public static final ServiceExceptionDefinition LAUNCHER_SYSTEM_BUSY =
            new ServiceExceptionDefinition(10007, "系统繁忙～");

    public static final ServiceExceptionDefinition LAUNCHER_ADMIN_PERMISSION_DENY =
            new ServiceExceptionDefinition(10008, "管理员权限不足");

    public static final ServiceExceptionDefinition LAUNCHER_OPEN_PLATFORM_CHECK_FAILED =
            new ServiceExceptionDefinition(10009, "开放平台签名验证失败");

    public static final ServiceExceptionDefinition LAUNCHER_OPEN_PLATFORM_TIMESTAMP_CHECKED =
            new ServiceExceptionDefinition(10010, "时间戳与服务器时间出入过大");

    public static final ServiceExceptionDefinition LAUNCHER_PRICE_FORMAT_EXCEPTION =
            new ServiceExceptionDefinition(10011, "价格格式不正确！");

    public static final ServiceExceptionDefinition LAUNCHER_OPEN_CLIENT_CODE_CANNOT_BE_NULL =
            new ServiceExceptionDefinition(10012, "开放平台客户编码不能为空");

    public static final ServiceExceptionDefinition LAUNCHER_READ_FILE_JUST_SUPPORT_MULTIPART =
            new ServiceExceptionDefinition(10013, "请使用文件上传格式报文上传文件");

    public static final ServiceExceptionDefinition LAUNCHER_GET_IP_FAILED =
            new ServiceExceptionDefinition(10014, "获取用户IP失败，请联系管理员");

}

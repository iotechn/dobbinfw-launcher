package com.dobbinsoft.fw.launcher.controller;

import cn.hutool.crypto.CryptoException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.dobbinsoft.fw.core.Const;
import com.dobbinsoft.fw.core.annotation.HttpMethod;
import com.dobbinsoft.fw.core.annotation.HttpParam;
import com.dobbinsoft.fw.core.annotation.HttpParamType;
import com.dobbinsoft.fw.core.annotation.ResultType;
import com.dobbinsoft.fw.core.annotation.param.NotNull;
import com.dobbinsoft.fw.core.annotation.param.Range;
import com.dobbinsoft.fw.core.annotation.param.TextFormat;
import com.dobbinsoft.fw.core.entiy.inter.IdentityOwner;
import com.dobbinsoft.fw.core.entiy.inter.PermissionOwner;
import com.dobbinsoft.fw.core.exception.ServiceException;
import com.dobbinsoft.fw.core.model.GatewayResponse;
import com.dobbinsoft.fw.core.util.ISessionUtil;
import com.dobbinsoft.fw.launcher.exception.LauncherExceptionDefinition;
import com.dobbinsoft.fw.launcher.exception.LauncherServiceException;
import com.dobbinsoft.fw.launcher.exception.OtherExceptionTransfer;
import com.dobbinsoft.fw.launcher.exception.OtherExceptionTransferHolder;
import com.dobbinsoft.fw.launcher.inter.AfterHttpMethod;
import com.dobbinsoft.fw.launcher.inter.BeforeHttpMethod;
import com.dobbinsoft.fw.launcher.inter.BeforeProcess;
import com.dobbinsoft.fw.launcher.invoker.CustomInvoker;
import com.dobbinsoft.fw.launcher.log.AccessLog;
import com.dobbinsoft.fw.launcher.log.AccessLogger;
import com.dobbinsoft.fw.launcher.manager.IApiManager;
import com.dobbinsoft.fw.launcher.permission.IAdminAuthenticator;
import com.dobbinsoft.fw.launcher.permission.IUserAuthenticator;
import com.dobbinsoft.fw.support.component.open.OpenPlatform;
import com.dobbinsoft.fw.support.component.open.model.OPData;
import com.dobbinsoft.fw.support.rate.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * Description:
 * User: rize
 * Date: 2018-08-08
 * Time: 下午11:00
 */
@Controller
@RequestMapping("/m.api")
public class ApiController {

    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private BeforeProcess beforeProcess;

    @Autowired(required = false)
    private BeforeHttpMethod beforeHttpMethod;

    @Autowired(required = false)
    private AfterHttpMethod afterHttpMethod;

    @Autowired
    private ISessionUtil sessionUtil;

    @Autowired
    private IUserAuthenticator userAuthenticator;

    @Autowired
    private IAdminAuthenticator adminAuthenticator;

    @Autowired(required = false)
    private AccessLogger accessLogger;

    @Autowired(required = false)
    private OpenPlatform openPlatform;

    @Autowired
    private CustomInvoker customInvoker;

    @Autowired
    private RateLimiter rateLimiter;

    @Value("${com.iotechn.unimall.env:1}")
    private String ENV;

    @Autowired
    private OtherExceptionTransferHolder otherExceptionTransferHolder;

    @RequestMapping(method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseBody
    public String invoke(HttpServletRequest req, HttpServletResponse res, @RequestBody(required = false) String requestBody) {
        long invokeTime = System.currentTimeMillis();
        try {
            logger.info("[HTTP] requestId={}; request={}", invokeTime, JSONObject.toJSONString(req.getParameterMap()));
            Object obj = process(req, res, requestBody, invokeTime);
            if (Const.IGNORE_PARAM_LIST.contains(obj.getClass())) {
                return obj.toString();
            }
            return afterPost(res, invokeTime, obj);
        } catch (ServiceException e) {
            GatewayResponse gatewayResponse = new GatewayResponse();
            gatewayResponse.setTimestamp(invokeTime);
            gatewayResponse.setErrno(e.getCode());
            gatewayResponse.setErrmsg(e.getMessage());
            gatewayResponse.setData(e.getAttach());
            return afterPost(res, invokeTime, gatewayResponse);
        } finally {
            if (openPlatform != null) {
                openPlatform.removeClientCode();
            }
        }
    }

    /**
     * 后置通知 抽取方法
     * @param res 响应
     * @param invokeTime 调用开始时间
     * @param obj 调用结果
     * @return
     */
    private String afterPost(HttpServletResponse res, long invokeTime, Object obj) {
        String result = JSONObject.toJSONString(obj, SerializerFeature.WriteMapNullValue, SerializerFeature.WriteDateUseDateFormat);
        long during = System.currentTimeMillis() - invokeTime;
        logger.info("[HTTP] requestId={}; response={}; during={}ms", invokeTime, JSONObject.toJSONString(result), during);
        if (afterHttpMethod != null) {
            afterHttpMethod.after(res, result);
        }
        return result;
    }


    private Object process(HttpServletRequest request, HttpServletResponse response, String requestBody, long invokeTime) throws ServiceException {
        try {
            if (this.beforeProcess != null) {
                this.beforeProcess.before(request);
            }
            String contentType = request.getContentType();
            Map<String, String[]> parameterMap;
            // 是否忽略管理员登录，若走的是开放平台，则该字段为true
            boolean ignoreAdminLogin = false;
            // 若包含 ADMIN_ID || USER_ID 则此变量为true
            boolean isPrivateApi = false;
            if (!StringUtils.isEmpty(contentType) && contentType.indexOf("application/json") > -1) {
                // json 报文
                OPData opData = JSONObject.parseObject(requestBody, OPData.class);
                try {
                    if (StringUtils.isEmpty(opData.getClientCode())) {
                        throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_OPEN_CLIENT_CODE_CANNOT_BE_NULL);
                    }
                    parameterMap = openPlatform.decrypt(opData.getClientCode(), opData.getCiphertext());
                } catch (CryptoException e) {
                    throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_OPEN_PLATFORM_CHECK_FAILED);
                }
                Long optimestamp = Long.parseLong(parameterMap.get("optimestamp")[0]);
                // TODO API 白名单校验
                if (Math.abs(optimestamp - System.currentTimeMillis()) > 1000L * 60 * 3) {
                    throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_OPEN_PLATFORM_TIMESTAMP_CHECKED);
                }
                openPlatform.setClientCode(opData.getClientCode());
                ignoreAdminLogin = true;
            } else {
                parameterMap = request.getParameterMap();
            }
            IApiManager apiManager = applicationContext.getBean(IApiManager.class);
            String[] gps = parameterMap.get("_gp");
            String[] mts = parameterMap.get("_mt");
            String[] apps = parameterMap.get("_app");
            if (gps == null || mts == null || gps.length == 0 || mts.length == 0) {
                throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_API_NOT_EXISTS);
            }
            String _gp = gps[0];
            String _mt = mts[0];
            // 决定路由到某个系统，单体应用可忽略此字段。
            String _app = (apps == null || apps.length == 0) ? "_app" : apps[0];
            String[] _types = parameterMap.get("_type");
            String _type = null;
            if (_types != null && _types.length > 0) {
                _type = _types[0];
            }
            Method method = apiManager.getMethod(_app, _gp, _mt);
            if (method == null) {
                throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_API_NOT_EXISTS);
            }
            HttpMethod httpMethod = method.getAnnotation(HttpMethod.class);
            if (httpMethod == null) {
                //只起标记作用防止调到封闭方法了
                throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_API_NOT_EXISTS);
            }
            if (this.beforeHttpMethod != null) {
                this.beforeHttpMethod.before(request, _gp, _mt, httpMethod);
            }
            String permission = httpMethod.permission();
            if (!StringUtils.isEmpty(permission)) {
                //若需要权限，则校验当前用户是否具有权限
                String accessToken = request.getHeader(Const.ADMIN_ACCESS_TOKEN);
                PermissionOwner adminDTO = adminAuthenticator.getAdmin(accessToken);
                sessionUtil.setAdmin(adminDTO);
                if ((adminDTO == null || !sessionUtil.hasPerm(permission)) && !ignoreAdminLogin) {
                    /**
                     * 权限不足有两种可能
                     * 1. 可走开放平台
                     * 2. 确实没有权限，也不走开放平台，则抛出异常
                     */
                    if (adminDTO == null) {
                        throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_ADMIN_NOT_LOGIN);
                    } else {
                        String permissionRoute = apiManager.getPermissionRoute(permission);
                        if (!StringUtils.isEmpty(permissionRoute)) {
                            throw new LauncherServiceException("权限不足，请分配 " + permissionRoute, LauncherExceptionDefinition.LAUNCHER_ADMIN_PERMISSION_DENY.getCode());
                        }
                        throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_ADMIN_PERMISSION_DENY);
                    }
                }
                // 对有权限的方法进行日志记录
                if (accessLogger != null) {
                    if (adminDTO != null) {
                        AccessLog accessLog = new AccessLog();
                        accessLog.setAdminId(adminDTO.getId());
                        accessLog.setGroup(_gp);
                        accessLog.setMethod(_mt);
                        accessLog.setRequestId(invokeTime);
                        accessLogger.save(accessLog);
                    }
                }
            }
            Object serviceBean = apiManager.getServiceBean(method);
            Parameter[] methodParameters = method.getParameters();
            Object[] args = new Object[methodParameters.length];
            // 用户或管理员的ID，用于限流
            Long personId = null;
            String ip;
            if (ENV.equals("1")) {
                //若是开发环境
                ip = "27.10.60.71";
            } else {
                ip = request.getHeader("X-Forwarded-For");
                if (StringUtils.isEmpty(ip)) {
                    ip = request.getHeader("X-Forward-For");
                }
                if (StringUtils.isEmpty(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
            }
            for (int i = 0; i < methodParameters.length; i++) {
                Parameter methodParam = methodParameters[i];
                HttpParam httpParam = methodParam.getAnnotation(HttpParam.class);
                if (httpParam == null) {
                    throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_API_NOT_EXISTS);
                }
                if (httpParam.type() == HttpParamType.COMMON) {
                    String[] paramArray = parameterMap.get(httpParam.name());
                    if (paramArray != null && paramArray.length > 0 && !StringUtils.isEmpty(paramArray[0])) {
                        Class<?> type = methodParam.getType();
                        //参数校验
                        checkParam(type, methodParam, paramArray[0]);
                        if (String.class == type) {
                            args[i] = paramArray[0];
                        } else if (Const.IGNORE_PARAM_LIST.contains(type)) {
                            Constructor<?> constructor = type.getConstructor(String.class);
                            args[i] = constructor.newInstance(paramArray[0]);
                        } else if (type.isArray()) {
                            //若是数组
                            Class<?> itemType = type.getComponentType();
                            Object realType[] = (Object[]) Array.newInstance(itemType, paramArray.length);
                            if (paramArray.length > 0) {
                                for (int j = 0; j < paramArray.length; j++) {
                                    if (Const.IGNORE_PARAM_LIST.contains(itemType)) {
                                        Constructor<?> constructor = itemType.getConstructor(String.class);
                                        realType[j] = constructor.newInstance(paramArray[j]);
                                    } else {
                                        realType[j] = JSONObject.parseObject(paramArray[j], itemType);
                                    }
                                }
                            }
                            args[i] = realType;
                        } else if (type == List.class) {
                            args[i] = JSONObject.parseArray(paramArray[0], httpParam.arrayClass());
                        } else {
                            //Json解析
                            args[i] = JSONObject.parseObject(paramArray[0], type);
                            this.checkParam(args[i]);
                        }
                    } else {
                        if (!StringUtils.isEmpty(httpParam.valueDef())) {
                            //若有默认值
                            Class<?> type = methodParam.getType();
                            Constructor<?> constructor = type.getConstructor(String.class);
                            args[i] = constructor.newInstance(httpParam.valueDef());
                        } else {
                            NotNull notNull = methodParam.getAnnotation(NotNull.class);
                            if (notNull != null) {
                                logger.error("missing :" + httpParam.name());
                                this.throwParamCheckServiceException(notNull);
                            }
                            args[i] = null;
                        }
                    }
                } else if (httpParam.type() == HttpParamType.MONEY) {
                    // 若是钱，则
                    String[] paramArray = parameterMap.get(httpParam.name());
                    if (paramArray != null && paramArray.length > 0 && !StringUtils.isEmpty(paramArray[0])) {
                        String priceRaw = paramArray[0];
                        try {
                            args[i] = Integer.parseInt((Float.parseFloat(priceRaw) * 100) + "");
                        } catch (NumberFormatException e) {
                            throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_PRICE_FORMAT_EXCEPTION);
                        }
                    } else {
                        if (!StringUtils.isEmpty(httpParam.valueDef())) {
                            //若有默认值
                            args[i] = Integer.parseInt((Float.parseFloat(httpParam.valueDef()) * 100) + "");
                        } else {
                            NotNull notNull = methodParam.getAnnotation(NotNull.class);
                            if (notNull != null) {
                                logger.error("missing :" + httpParam.name());
                                this.throwParamCheckServiceException(notNull);
                            }
                            args[i] = null;
                        }
                    }
                } else if (httpParam.type() == HttpParamType.USER_ID) {
                    String accessToken = request.getHeader(Const.USER_ACCESS_TOKEN);
                    IdentityOwner user = userAuthenticator.getUser(accessToken);
                    if (user != null) {
                        args[i] = user.getId();
                        personId = user.getId();
                    }
                    isPrivateApi = true;
                    if (args[i] == null && methodParam.getAnnotation(NotNull.class) != null) {
                        throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_USER_NOT_LOGIN);
                    }
                } else if (httpParam.type() == HttpParamType.ADMIN_ID) {
                    String accessToken = request.getHeader(Const.ADMIN_ACCESS_TOKEN);
                    PermissionOwner adminDTO = adminAuthenticator.getAdmin(accessToken);
                    if (adminDTO != null) {
                        args[i] = adminDTO.getId();
                        personId = adminDTO.getId();
                    }
                    isPrivateApi = true;
                    if (args[i] == null && methodParam.getAnnotation(NotNull.class) != null && !ignoreAdminLogin) {
                        /**
                         * 管理员为空，有两种情况
                         * 1. 可走开放平台
                         * 2. 不可走开放平台，需要但需验签
                         */
                        throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_ADMIN_NOT_LOGIN);
                    }
                } else if (httpParam.type() == HttpParamType.IP) {
                    //这里根据实际情况来定。 若使用了负载均衡，Ip将会被代理服务器设置到某个Header里面
                    args[i] = ip;
                    if (StringUtils.isEmpty(args[i]) && methodParam.getAnnotation(NotNull.class) != null && !ignoreAdminLogin) {
                        throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_GET_IP_FAILED);
                    }
                } else if (httpParam.type() == HttpParamType.HEADER) {
                    String header = request.getHeader(httpParam.name());
                    args[i] = header;
                    NotNull annotation = methodParam.getAnnotation(NotNull.class);
                    if (header == null && annotation != null) {
                        this.throwParamCheckServiceException(annotation);
                    }
                } else if (httpParam.type() == HttpParamType.FILE) {
                    // 读文件
                    if (request instanceof MultipartHttpServletRequest) {
                        MultipartHttpServletRequest multipartHttpServletRequest = (MultipartHttpServletRequest) request;
                        MultipartFile file = multipartHttpServletRequest.getFile(httpParam.name());
                        NotNull annotation = methodParam.getAnnotation(NotNull.class);
                        if (file == null && annotation != null) {
                            this.throwParamCheckServiceException(annotation);
                        }
                        if (file != null) {
                            InputStream inputStream = null;
                            try {
                                inputStream = file.getInputStream();
                                byte[] bytes = StreamUtils.copyToByteArray(inputStream);
                                args[i] = bytes;
                            } catch (IOException e) {
                                throw e;
                            } finally {
                                if (inputStream != null)
                                    inputStream.close();
                            }
                        }
                    } else {
                        throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_READ_FILE_JUST_SUPPORT_MULTIPART);
                    }
                }
                if (ignoreAdminLogin && isPrivateApi && httpMethod.openPlatform()) {
                    throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_OPEN_PLATFORM_CHECK_FAILED);
                }
            }
            // 流量限制
            if (!this.rateLimiter.acquire(_gp + "." + _mt, httpMethod, personId, ip)) {
                throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_SYSTEM_BUSY);
            }
            ClassLoader classLoader = serviceBean.getClass().getClassLoader();
            Thread.currentThread().setContextClassLoader(classLoader);
            Object invokeObj = customInvoker.invoke(serviceBean, method, args);
            ResultType resultType = httpMethod.type();
            if (!StringUtils.isEmpty(_type) && "raw".equals(_type)) {
                //如果是不用包装的直接返回
                return invokeObj;
            }
            //下面是需要包装返回的
            if (resultType == ResultType.COOKIE) {
                //加入Cookie时处理
                if (StringUtils.isEmpty(httpMethod.retName())) {
                    throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_API_NOT_EXISTS);
                } else {
                    //setCookie
                    Cookie cookie = new Cookie(httpMethod.retName(), (String) invokeObj);
                    cookie.setPath("/");
                    if (httpMethod.maxAge() != -1) {
                        cookie.setMaxAge(httpMethod.maxAge());
                    }
                    response.addCookie(cookie);
                }
            }
            GatewayResponse gatewayResponse = new GatewayResponse();
            gatewayResponse.setErrno(200);
            gatewayResponse.setErrmsg("成功");
            gatewayResponse.setTimestamp(invokeTime);
            gatewayResponse.setData(invokeObj);
            return gatewayResponse;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            Throwable target = e;
            logger.info(e.getClass().getClassLoader() + "");
            if (e instanceof InvocationTargetException) {
                InvocationTargetException proxy = (InvocationTargetException) e;
                Throwable targetException = proxy.getTargetException();
                target = targetException;
                if (targetException instanceof ServiceException) {
                    throw (ServiceException) targetException;
                }
            }
            Class<? extends Throwable> clazz = target.getClass();
            OtherExceptionTransfer transfer = otherExceptionTransferHolder.getByClass(clazz);
            ServiceException afterTransServiceException = transfer.trans(e);
            if (afterTransServiceException != null) {
                logger.error("[网关] 系统未知异常 message=" + afterTransServiceException.getMessage(), e);
                throw afterTransServiceException;
            }
            logger.error("[网关] 系统未知异常", e);
            throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_UNKNOWN_EXCEPTION);
        }
    }

    /**
     * 校验细粒度接口参数
     *
     * @param type
     * @param methodParam
     * @param target
     * @throws ServiceException
     */
    private void checkParam(Class<?> type, Parameter methodParam, String target) throws ServiceException {
        if (type == String.class) {
            TextFormat textFormat = methodParam.getAnnotation(TextFormat.class);
            if (textFormat != null) {
                String regex = textFormat.regex();
                if (!StringUtils.isEmpty(regex)) {
                    //如果正则生效，则直接使用正则校验
                    if (!target.matches(regex)) {
                        this.throwParamCheckServiceException(textFormat);
                    }
                } else {
                    boolean notChinese = textFormat.notChinese();
                    if (notChinese) {
                        if (target.matches("[\\u4e00-\\u9fa5]+")) {
                            this.throwParamCheckServiceException(textFormat);
                        }
                    }

                    String[] contains = textFormat.contains();
                    for (int j = 0; j < contains.length; j++) {
                        if (!target.contains(contains[j])) {
                            this.throwParamCheckServiceException(textFormat);
                        }
                    }

                    String[] notContains = textFormat.notContains();
                    for (int j = 0; j < notContains.length; j++) {
                        if (target.contains(notContains[j])) {
                            this.throwParamCheckServiceException(textFormat);
                        }
                    }

                    String startWith = textFormat.startWith();
                    if (!StringUtils.isEmpty(startWith)) {
                        if (!target.startsWith(startWith)) {
                            this.throwParamCheckServiceException(textFormat);
                        }
                    }

                    String endsWith = textFormat.endsWith();
                    if (!StringUtils.isEmpty(target)) {
                        if (!target.endsWith(endsWith)) {
                            this.throwParamCheckServiceException(textFormat);
                        }
                    }
                    int targetLength = target.length();
                    int length = textFormat.length();
                    if (length != -1) {
                        if (targetLength != length) {
                            this.throwParamCheckServiceException(textFormat);
                        }
                    }

                    if (targetLength < textFormat.lengthMin()) {
                        this.throwParamCheckServiceException(textFormat);
                    }

                    if (targetLength > textFormat.lengthMax()) {
                        this.throwParamCheckServiceException(textFormat);
                    }
                }
            }
        } else if (type == Integer.class) {
            Range range = methodParam.getAnnotation(Range.class);
            Integer integer = new Integer(target);
            if (range != null) {
                if (integer > range.max() || integer < range.min()) {
                    this.throwParamCheckServiceException(range);
                }
            }
        } else if (type == Long.class) {
            Range range = methodParam.getAnnotation(Range.class);
            if (range != null) {
                Long integer = new Long(target);
                if (integer > range.max() || integer < range.min()) {
                    this.throwParamCheckServiceException(range);
                }
            }
        } else if (type == Float.class) {
            Range range = methodParam.getAnnotation(Range.class);
            if (range != null) {
                Float number = new Float(target);
                if (number > range.max() || number < range.min()) {
                    this.throwParamCheckServiceException(range);
                }
            }
        } else if (type == Double.class) {
            Range range = methodParam.getAnnotation(Range.class);
            if (range != null) {
                Double number = new Double(target);
                if (number > range.max() || number < range.min()) {
                    this.throwParamCheckServiceException(range);
                }
            }
        }
    }

    /**
     * 校验粗粒度接口参数，递归校验
     *
     * @param object
     * @throws ServiceException
     */
    private void checkParam(Object object) throws ServiceException {
        try {
            Class<?> objectClazz = object.getClass();
            Field[] declaredFields = objectClazz.getDeclaredFields();
            for (Field field : declaredFields) {
                field.setAccessible(true);
                // 1. 非空
                NotNull notNull = field.getAnnotation(NotNull.class);
                if (notNull != null && notNull.reqScope() && ObjectUtils.isEmpty(field.get(object))) {
                    this.throwParamCheckServiceException(notNull);
                }
                // 2. 范围
                Class<?> fieldClazz = field.getType();
                if (Number.class.isAssignableFrom(fieldClazz)) {
                    Range range = field.getAnnotation(Range.class);
                    Object numberObject = field.get(object);
                    if (numberObject != null) {
                        if (fieldClazz == Integer.class) {
                            if (range != null) {
                                Integer number = new Integer(numberObject.toString());
                                if (number > range.max() || number < range.min()) {
                                    this.throwParamCheckServiceException(range);
                                }
                            }
                        } else if (fieldClazz == Long.class) {
                            if (range != null) {
                                Long number = new Long(numberObject.toString());
                                if (number > range.max() || number < range.min()) {
                                    this.throwParamCheckServiceException(range);
                                }
                            }
                        } else if (fieldClazz == Float.class) {
                            if (range != null) {
                                Float number = new Float(numberObject.toString());
                                if (number > range.max() || number < range.min()) {
                                    this.throwParamCheckServiceException(range);
                                }
                            }
                        } else if (fieldClazz == Double.class) {
                            if (range != null) {
                                Double number = new Double(numberObject.toString());
                                if (number > range.max() || number < range.min()) {
                                    this.throwParamCheckServiceException(range);
                                }
                            }
                        }
                    }
                }
                // 3. 递归其他非基本类型
                if (!Modifier.isStatic(field.getModifiers()) && !Const.IGNORE_PARAM_LIST.contains(fieldClazz)) {
                    if (Collection.class.isAssignableFrom(fieldClazz)) {
                        // 3.1. Collection
                        Collection collection = (Collection) field.get(object);
                        if (collection != null) {
                            for (Object obj : collection) {
                                if (!Const.IGNORE_PARAM_LIST.contains(obj.getClass())) {
                                    this.checkParam(obj);
                                }
                            }
                        }
                    } else {
                        // 3.2. 其他对象
                        Object obj = field.get(object);
                        if (obj != null) {
                            this.checkParam(obj);
                        }
                    }
                }
            }
        } catch (IllegalAccessException e) {
        }
    }

    private void throwParamCheckServiceException(Annotation annotation) throws ServiceException {
        try {
            Method method = annotation.getClass().getMethod("message");
            Object res = method.invoke(annotation);
            if (!ObjectUtils.isEmpty(res)) {
                throw new LauncherServiceException((String) res, LauncherExceptionDefinition.LAUNCHER_PARAM_CHECK_FAILED.getCode());
            } else {
                throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_PARAM_CHECK_FAILED);
            }
        } catch (NoSuchMethodException e) {
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        }
    }

}

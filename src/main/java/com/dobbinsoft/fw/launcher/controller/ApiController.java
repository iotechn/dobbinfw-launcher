package com.dobbinsoft.fw.launcher.controller;

import com.dobbinsoft.fw.core.Const;
import com.dobbinsoft.fw.core.annotation.HttpExcel;
import com.dobbinsoft.fw.core.annotation.HttpMethod;
import com.dobbinsoft.fw.core.annotation.HttpParam;
import com.dobbinsoft.fw.core.annotation.HttpParamType;
import com.dobbinsoft.fw.core.annotation.param.NotNull;
import com.dobbinsoft.fw.core.entiy.inter.CustomAccountOwner;
import com.dobbinsoft.fw.core.entiy.inter.IdentityOwner;
import com.dobbinsoft.fw.core.entiy.inter.PermissionOwner;
import com.dobbinsoft.fw.core.exception.CoreExceptionDefinition;
import com.dobbinsoft.fw.core.exception.ServiceException;
import com.dobbinsoft.fw.core.model.GatewayResponse;
import com.dobbinsoft.fw.core.util.ISessionUtil;
import com.dobbinsoft.fw.launcher.exception.OtherExceptionTransfer;
import com.dobbinsoft.fw.launcher.exception.OtherExceptionTransferHolder;
import com.dobbinsoft.fw.launcher.inter.AfterHttpMethod;
import com.dobbinsoft.fw.launcher.inter.BeforeHttpMethod;
import com.dobbinsoft.fw.launcher.inter.BeforeProcess;
import com.dobbinsoft.fw.launcher.invoker.CustomInvoker;
import com.dobbinsoft.fw.launcher.manager.IApiManager;
import com.dobbinsoft.fw.launcher.permission.IAdminAuthenticator;
import com.dobbinsoft.fw.launcher.permission.ICustomAuthenticator;
import com.dobbinsoft.fw.launcher.permission.IUserAuthenticator;
import com.dobbinsoft.fw.launcher.ws.DefaultHandshakeInterceptor;
import com.dobbinsoft.fw.launcher.ws.DefaultWebSocketHandler;
import com.dobbinsoft.fw.support.model.SseEmitterWrapper;
import com.dobbinsoft.fw.support.model.WsWrapper;
import com.dobbinsoft.fw.support.rate.RateLimiter;
import com.dobbinsoft.fw.support.rpc.RpcContextHolder;
import com.dobbinsoft.fw.support.rpc.RpcProviderUtils;
import com.dobbinsoft.fw.support.sse.SSEPublisher;
import com.dobbinsoft.fw.support.utils.*;
import com.dobbinsoft.fw.support.utils.excel.ExcelBigExportAdapter;
import com.dobbinsoft.fw.support.utils.excel.ExcelData;
import com.dobbinsoft.fw.support.utils.excel.ExcelUtils;
import com.dobbinsoft.fw.support.ws.WsPublisher;
import com.dobbinsoft.fw.support.ws.event.WsEventReceiver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API Controller，RPC/WEB 调用流量入口。统一的方法路由，参数校验等逻辑。
 */
@RestController
@RequestMapping("/")
@EnableWebSocket
public class ApiController implements WebSocketConfigurer, DefaultHandshakeInterceptor, DefaultWebSocketHandler {

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
    private ICustomAuthenticator customAuthenticator;

    @Autowired
    private CustomInvoker customInvoker;

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private OtherExceptionTransferHolder otherExceptionTransferHolder;

    @Autowired
    private ServerProperties serverProperties;

    @Autowired(required = false)
    private RpcProviderUtils rpcProviderUtils;

    @Autowired(required = false)
    private SSEPublisher ssePublisher;

    @Autowired(required = false)
    private WsPublisher wsPublisher;

    @Autowired(required = false)
    private WsEventReceiver wsEventReceiver;

    private static final String APPLICATION_XLS_X = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final String WS_CURRENT_IDENTITY_OWNER_KEY = "WS_CURRENT_IDENTITY_OWNER_KEY";

    private static final Pattern URI_PATTERN = Pattern.compile("/ws\\.api/([^/]+)/([^/]+)");

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        if (wsPublisher != null) {
            registry.addHandler(this, "/ws.api", "/ws.api/{_gp}/{_mt}")
                    .setAllowedOrigins("*")
                    .addInterceptors(this);  // 添加拦截器进行认证
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object o = session.getAttributes().get(WS_CURRENT_IDENTITY_OWNER_KEY);
        logger.info("[WS] New Session created, session id: {}", session.getId());
        String identityOwnerKey = o.toString();
        wsPublisher.join(identityOwnerKey, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        Object o = session.getAttributes().get(WS_CURRENT_IDENTITY_OWNER_KEY);
        logger.info("[WS] Session closed, session id: {}", session.getId());
        String identityOwnerKey = o.toString();
        wsPublisher.quit(identityOwnerKey);
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        Object payload = message.getPayload();
        if (payload instanceof String) {
            String event = payload.toString();
            JsonNode jsonNode = JacksonUtil.parseObject(event);
            JsonNode eventTypeObj = jsonNode.get("eventType");
            if (eventTypeObj == null) {
                logger.info("[WS] Event 报文格式不正确 event: {}", event);
            } else {
                String eventType = eventTypeObj.asText();
                wsEventReceiver.route(eventType, event);
            }
        } else {
            logger.info("[WS] Session 仅支持text报文, session id: {}", session.getId());
        }
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (wsPublisher == null) {
            logger.info("[WS] 请使用@EnableWs主机 激活Websocket功能");
            return false;
        }
        long invokeTime = System.currentTimeMillis();
        HttpServletRequest req;
        if (request instanceof ServletServerHttpRequest) {
            req = ((ServletServerHttpRequest) request).getServletRequest();
        } else {
            logger.warn("[WS] 目前仅支持Http握手");
            return false;
        }
        HttpServletResponse res;
        if (response instanceof ServletServerHttpResponse) {
            res = ((ServletServerHttpResponse) response).getServletResponse();
        } else {
            logger.warn("[WS] 目前仅支持Http握手");
            return false;
        }

        String requestURI = req.getRequestURI();
        if (serverProperties.getServlet() != null &&
                StringUtils.isNotEmpty(serverProperties.getServlet().getContextPath())) {
            requestURI = requestURI.substring(serverProperties.getServlet().getContextPath().length());
        }
        // 解析路径参数
        Matcher matcher = URI_PATTERN.matcher(requestURI);
        String _gp = null;
        String _mt = null;
        if (matcher.matches()) {
            // 提取路径参数
            _gp = matcher.group(1); // 第一个路径参数
            _mt = matcher.group(2); // 第二个路径参数
        }
        ApiContext context = findContext(req, _gp, _mt, ApiEntry.WS);
        try {
            Object obj = process(req, res, context);
            if (obj instanceof WsWrapper) {
                // 鉴权 & 握手完成， 后续进入afterConnectionEstablished方法
                attributes.put(WS_CURRENT_IDENTITY_OWNER_KEY, ((WsWrapper) obj).getIdentityOwnerKey());
                return true;
            }
            logger.error("[WS] 方法返回对象并非 WsWrapper");
            return false;
        } catch (ServiceException e) {
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            byte[] result = buildServiceResult(res, invokeTime, e).getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = res.getOutputStream()) {
                os.write(result);
            }
        } finally {
            MDC.clear();
            sessionUtil.clear();
        }
        return false;
    }


    /**
     * 远程调用时 接口地址， 通常nginx配置时不暴露
     * @param req
     * @param res
     * @param _gp
     * @param _mt
     * @throws IOException
     */
    @RequestMapping(value = {"/rpc", "/rpc/{_gp}/{_mt}"}, method = {RequestMethod.POST, RequestMethod.GET})
    public void rpcInvoke(
            HttpServletRequest req,
            HttpServletResponse res,
            @PathVariable(required = false) String _gp,
            @PathVariable(required = false) String _mt) throws IOException {
        try {
            // Valid rpc token
            String jwtToken = req.getHeader(Const.RPC_HEADER);
            String systemId = req.getHeader(Const.RPC_SYSTEM_ID);
            if (rpcProviderUtils == null) {
                logger.error("[RPC] You need to add @EnableRpc");
            }
            JwtUtils.JwtResult jwtResult = rpcProviderUtils.validToken(systemId, jwtToken);
            if (jwtResult.getResult() != JwtUtils.Result.SUCCESS) {
                throw new ServiceException(CoreExceptionDefinition.LAUNCHER_RPC_SIGN_INCORRECT);
            }
            String rpcContextJson = req.getHeader(Const.RPC_CONTEXT_JSON);
            if (StringUtils.isNotEmpty(rpcContextJson)) {
                Map<String, String> rpcContexts = JacksonUtil.parseObject(rpcContextJson, new TypeReference<Map<String, String>>() {
                });
                rpcContexts.forEach(RpcContextHolder::add);
            }
        } catch (ServiceException e) {
            // 异常
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            try (OutputStream os = res.getOutputStream()) {
                byte[] result = buildServiceResult(res, System.currentTimeMillis(), e).getBytes(StandardCharsets.UTF_8);
                os.write(result);
                return;
            }
        }
        commonsInvoke(req, res, _gp, _mt, ApiEntry.RPC);
    }

    /**
     * sse 请求时地址，返回的content-type为text/event-stream
     * @param req
     * @param res
     * @param _gp
     * @param _mt
     * @return
     * @throws IOException
     */
    @RequestMapping(value = {"/sse.api", "/sse.api/{_gp}/{_mt}"}, method = {RequestMethod.POST, RequestMethod.GET})
    public SseEmitter sseInvoke(
            HttpServletRequest req,
            HttpServletResponse res,
            @PathVariable(required = false) String _gp,
            @PathVariable(required = false) String _mt) throws IOException {
        long invokeTime = System.currentTimeMillis();
        try {
            ApiContext context = this.findContext(req, _gp, _mt, ApiEntry.SSE);
            Object object = process(req, res, context);
            if (object instanceof SseEmitterWrapper wrapper) {
                if (ssePublisher == null) {
                    throw new ServiceException(CoreExceptionDefinition.LAUNCHER_NOT_SUPPORT);
                }
                logger.info("[HTTP] R=SSE 建立, IdentityOwnerKey={}", wrapper.getIdentityOwnerKey());
                return wrapper.getSseEmitter();
            } else if (object instanceof SseEmitter sseEmitter){
                logger.info("[HTTP] R=SSE 建立");
                return sseEmitter;
            } else {
                throw new ServiceException(CoreExceptionDefinition.LAUNCHER_NOT_SUPPORT);
            }
        } catch (ServiceException e) {
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            byte[] result = buildServiceResult(res, invokeTime, e).getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = res.getOutputStream()) {
                os.write(result);
            }
            return null;
        } finally {
            MDC.clear();
            sessionUtil.clear();
        }
    }

    /**
     * 普通请求
     * @param req
     * @param res
     * @param _gp
     * @param _mt
     * @throws IOException
     */
    @RequestMapping(value = {"/m.api", "/m.api/{_gp}/{_mt}"}, method = {RequestMethod.POST, RequestMethod.GET})
    public void invoke(
            HttpServletRequest req,
            HttpServletResponse res,
            @PathVariable(required = false) String _gp,
            @PathVariable(required = false) String _mt) throws IOException {
        commonsInvoke(req, res, _gp, _mt, ApiEntry.WEB);
    }

    private void commonsInvoke(HttpServletRequest req, HttpServletResponse res, String _gp, String _mt, ApiEntry apiEntry) throws IOException {
        byte[] result;
        long invokeTime = System.currentTimeMillis();
        try {
            ApiContext context = this.findContext(req, _gp, _mt, apiEntry);
            Object obj = process(req, res, context);
            if (obj instanceof WsWrapper) {
                // 直接返回成功即可
                logger.info("[WS] 连接成功");
                return;
            }
            if (Const.IGNORE_PARAM_LIST.contains(obj.getClass())) {
                result = obj.toString().getBytes(StandardCharsets.UTF_8);
            } else if (context.httpExcel != null && obj instanceof GatewayResponse<?> gatewayResponse) {
                res.setContentType(APPLICATION_XLS_X);
                // 直接输出文件
                ExcelData<?> excelData = new ExcelData<>();
                Object respData = gatewayResponse.getData();
                String fileName = context.httpExcel.fileName() + "-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
                if (respData instanceof List<?>) {
                    List data = (List<?>) respData;
                    excelData.setData(data);
                    excelData.setFileName(fileName);
                    ExcelUtils.exportExcel(res, excelData, context.httpExcel.clazz());
                } else if (respData instanceof ExcelBigExportAdapter<?> excelBigExportAdapter) {
                    ExcelUtils.exportBigExcel(res, excelBigExportAdapter, fileName);
                } else {
                    throw new RuntimeException("Http Excel 只能返回List或者ExcelBigExportAdapter");
                }
                afterPost(res, invokeTime, obj, false, context.httpMethod.noLog());
                return;
            } else if (obj instanceof GatewayResponse<?> gatewayResponse) {
                gatewayResponse.setTimestamp(invokeTime);
                if (StringUtils.isNotEmpty(gatewayResponse.getContentType())) {
                    res.setContentType(gatewayResponse.getContentType());
                } else {
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                }
                Object data = gatewayResponse.getData();
                if (data instanceof byte[]) {
                    result = (byte[]) data;
                    afterPost(res, invokeTime, obj, false, context.httpMethod.noLog());
                } else {
                    result = afterPost(res, invokeTime, obj, true, context.httpMethod.noLog()).getBytes(StandardCharsets.UTF_8);
                }
            } else {
                res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                result = afterPost(res, invokeTime, obj, true, context.httpMethod.noLog()).getBytes(StandardCharsets.UTF_8);
            }
        } catch (ServiceException e) {
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            result = buildServiceResult(res, invokeTime, e).getBytes(StandardCharsets.UTF_8);
        } finally {
            MDC.clear();
            sessionUtil.clear();
        }
        try (OutputStream os = res.getOutputStream()) {
            os.write(result);
        }
    }

    /**
     * 后置通知 抽取方法
     * @param res 响应
     * @param invokeTime 调用开始时间
     * @param obj 调用结果
     * @return
     */
    private String afterPost(HttpServletResponse res, long invokeTime, Object obj, boolean json, boolean noLog) {
        long during = System.currentTimeMillis() - invokeTime;
        String result = "";
        if (json) {
            result = JacksonUtil.toJSONString(obj);
            if (noLog) {
                logger.info("[HTTP] R=NoLog, D={}ms", during);
            } else {
                logger.info("[HTTP] R={}; D={}ms", JacksonUtil.toJSONString(result), during);
            }
        } else {
            logger.info("[HTTP] R=NoLog, D={}ms", during);
        }
        if (afterHttpMethod != null) {
            afterHttpMethod.after(res, result);
        }
        return result;
    }


    /**
     * 获取请求上下文
     * @param request
     * @param _gp 已知晓_gp
     * @param _mt 已知晓_mt
     * @param apiEntry API入口，决定分组
     * @return
     * @throws ServiceException 服务异常
     */
    private ApiContext findContext(HttpServletRequest request, String _gp, String _mt, ApiEntry apiEntry) throws ServiceException {
        ApiContext apiContext = new ApiContext();
        // 判断请求类型
        String contentType = request.getContentType();
        if (MediaType.APPLICATION_JSON_VALUE.equals(contentType)) {
            try (InputStream is = request.getInputStream()) {
                String json = IOUtils.toString(is);
                Map<String, Object> map = JacksonUtil.toMap(json, String.class, Object.class);
                Map<String, String> newMap = new HashMap<>();
                if (map != null) {
                    // 支持Post空传参
                    map.forEach((k, v) -> {
                        if (v == null) {
                            return;
                        }
                        if (Const.IGNORE_PARAM_LIST.contains(v.getClass())) {
                            newMap.put(k, v.toString());
                        } else {
                            newMap.put(k, JacksonUtil.toJSONString(v));
                        }
                    });
                }
                apiContext.setParameterSingleMap(newMap);
            } catch (IOException e) {
                throw new ServiceException(CoreExceptionDefinition.LAUNCHER_IO_EXCEPTION);
            }

        } else if (StringUtils.isEmpty(contentType) || contentType.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE)){
            // GET请求时， contentType为null
            Map<String, String[]> parameterMap = request.getParameterMap();
            apiContext.setParameterMap(parameterMap);
        } else if (contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            Map<String, String[]> parameterMap = request.getParameterMap();
            apiContext.setParameterMap(parameterMap);
        } else {
            throw new ServiceException(CoreExceptionDefinition.LAUNCHER_CONTENT_TYPE_NOT_SUPPORT);
        }
        IApiManager apiManager = applicationContext.getBean(IApiManager.class);
        if (StringUtils.isNotEmpty(_gp) && StringUtils.isNotEmpty(_mt)) {
            apiContext._gp = _gp;
            apiContext._mt = _mt;
        } else {
            apiContext._gp = apiContext.getParameter("_gp");
            apiContext._mt = apiContext.getParameter("_mt");
            if (apiContext._gp == null || apiContext._mt == null) {
                throw new ServiceException(CoreExceptionDefinition.LAUNCHER_API_NOT_EXISTS);
            }
        }
        apiContext.entry = apiEntry;
        apiContext.method = apiEntry == ApiEntry.RPC ? apiManager.getRpcMethod(apiContext._gp, apiContext._mt) : apiManager.getMethod(apiContext._gp, apiContext._mt);
        if (apiContext.method == null) {
            throw new ServiceException(CoreExceptionDefinition.LAUNCHER_API_NOT_EXISTS);
        }
        apiContext.httpMethod = apiContext.method.getAnnotation(HttpMethod.class);
        if (apiContext.httpMethod == null) {
            //只起标记作用防止调到封闭方法了
            throw new ServiceException(CoreExceptionDefinition.LAUNCHER_API_NOT_EXISTS);
        }
        apiContext.httpExcel = apiContext.method.getAnnotation(HttpExcel.class);
        String trace = request.getHeader(Const.HTTP_TRACE_HEADER);
        if (StringUtils.isEmpty(trace)) {
            // 可能会冲突
            trace = System.currentTimeMillis() + "";
        }
        MDC.put("trace", trace);

        // 判断API URL是否正确
        if ((apiContext.method.getReturnType() == SseEmitterWrapper.class || apiContext.method.getReturnType() == SseEmitter.class) && apiEntry != ApiEntry.SSE) {
            throw new ServiceException(CoreExceptionDefinition.LAUNCHER_ONLY_SSE_SUPPORT);
        }
        if (apiContext.method.getReturnType() == WsWrapper.class && apiEntry != ApiEntry.WS) {
            throw new ServiceException(CoreExceptionDefinition.LAUNCHER_ONLY_WS_SUPPORT);
        }

        return apiContext;
    }


    /**
     * 调用HttpMethod
     * @param request
     * @param response
     * @param apiContext
     * @return 返回HttpMethod原始返回
     * @throws ServiceException
     */
    private Object process(HttpServletRequest request, HttpServletResponse response, ApiContext apiContext) throws ServiceException {
        try {
            if (this.beforeProcess != null) {
                this.beforeProcess.before(request);
            }
            IApiManager apiManager = applicationContext.getBean(IApiManager.class);
            Method method = apiContext.method;
            String _gp = apiContext._gp;
            String _mt = apiContext._mt;
            HttpMethod httpMethod = apiContext.httpMethod;

            logger.info("[HTTP] Q={}", JacksonUtil.toJSONString(apiContext.getParameterMap() == null ? apiContext.getParameterSingleMap() : apiContext.getParameterMap()));
            if (this.beforeHttpMethod != null) {
                this.beforeHttpMethod.before(request, _gp, _mt, httpMethod);
            }
            String permission = httpMethod.permission();
            if (StringUtils.isNotEmpty(permission)) {
                //若需要权限，则校验当前用户是否具有权限
                String accessToken = request.getHeader(Const.ADMIN_ACCESS_TOKEN);
                PermissionOwner adminDTO = adminAuthenticator.getAdmin(accessToken);
                sessionUtil.setAdmin(adminDTO);
                if ((adminDTO == null || !sessionUtil.hasPerm(permission))) {
                    // 没有权限
                    if (adminDTO == null) {
                        throw new ServiceException(CoreExceptionDefinition.LAUNCHER_ADMIN_NOT_LOGIN);
                    } else {
                        String permissionRoute = apiManager.getPermissionRoute(permission);
                        if (StringUtils.isNotEmpty(permissionRoute)) {
                            throw new ServiceException("权限不足，请分配 " + permissionRoute, CoreExceptionDefinition.LAUNCHER_ADMIN_PERMISSION_DENY.getCode());
                        }
                        throw new ServiceException(CoreExceptionDefinition.LAUNCHER_ADMIN_PERMISSION_DENY);
                    }
                }
            }
            Object serviceBean = apiManager.getServiceBean(method);
            Parameter[] methodParameters = method.getParameters();
            Object[] args = new Object[methodParameters.length];
            // 用户或管理员的ID，用于限流
            Long personId = null;
            String ip = RequestUtils.getClientIp(request);
            for (int i = 0; i < methodParameters.length; i++) {
                Parameter methodParam = methodParameters[i];
                HttpParam httpParam = methodParam.getAnnotation(HttpParam.class);
                if (httpParam == null) {
                    throw new ServiceException(CoreExceptionDefinition.LAUNCHER_API_NOT_EXISTS);
                }
                if (httpParam.type() == HttpParamType.COMMON) {
                    String value = apiContext.getParameter(httpParam.name());
                    if (StringUtils.isEmpty(value) && StringUtils.isNotEmpty(httpParam.valueDef())) {
                        value = httpParam.valueDef();
                    }
                    if (StringUtils.isNotEmpty(value)) {
                        Class<?> type = methodParam.getType();
                        ValidateUtils.checkParam(type, methodParam, value);
                        if (String.class == type) {
                            args[i] = value.trim();
                        } else if (Const.IGNORE_PARAM_LIST.contains(type)) {
                            Constructor<?> constructor = type.getConstructor(String.class);
                            try {
                                args[i] = constructor.newInstance(value);
                            } catch (NumberFormatException e) {
                                logger.warn("[HTTP] 客户端数字解析失败 value={}", value);
                                throw new ServiceException(CoreExceptionDefinition.LAUNCHER_NUMBER_PARSE_ERROR);
                            } catch (InvocationTargetException e) {
                                Throwable targetException = e.getTargetException();
                                if (targetException instanceof NumberFormatException) {
                                    logger.warn("[HTTP] 客户端数字解析失败 value={}", value);
                                    throw new ServiceException(CoreExceptionDefinition.LAUNCHER_NUMBER_PARSE_ERROR);
                                }
                            }
                        } else if (type == List.class) {
                            args[i] = JacksonUtil.parseArray(value, httpParam.arrayClass());
                        } else if (type == LocalDateTime.class) {
                            if (StringUtils.isNumeric(value)) {
                                args[i] = TimeUtils.timestampToLocalDate(Long.parseLong(value));
                            } else {
                                args[i] = TimeUtils.stringToLocalDateTime(value);
                            }
                        } else if (type == LocalDate.class) {
                            args[i] = TimeUtils.stringToLocalDate(value);
                        } else if (type == LocalTime.class) {
                            args[i] = TimeUtils.stringToLocalTime(value);
                        } else if (type == Date.class) {
                            args[i] = TimeUtils.stringToDate(value);
                        } else if (type == BigDecimal.class) {
                            args[i] = new BigDecimal(value);
                        } else {
                            //Json解析
                            args[i] = JacksonUtil.parseObject(value, type);
                            ValidateUtils.checkParam(args[i]);
                        }
                    } else {
                        if (StringUtils.isNotEmpty(httpParam.valueDef())) {
                            //若有默认值
                            Class<?> type = methodParam.getType();
                            Constructor<?> constructor = type.getConstructor(String.class);
                            args[i] = constructor.newInstance(httpParam.valueDef());
                        } else {
                            ValidateUtils.checkParam(methodParam.getType(), methodParam, httpParam.valueDef());
                            args[i] = null;
                        }
                    }
                } else if (httpParam.type() == HttpParamType.USER_ID) {
                    String accessToken = request.getHeader(Const.USER_ACCESS_TOKEN);
                    if (apiContext.entry == ApiEntry.WS && StringUtils.isEmpty(accessToken)) {
                        accessToken = request.getParameter(Const.USER_ACCESS_TOKEN);
                    }
                    IdentityOwner user = userAuthenticator.getUser(accessToken);
                    if (user != null) {
                        args[i] = user.getId();
                        personId = user.getId();
                        MDC.put("account", "USER_" + personId);
                        MDC.put("token", accessToken);
                    }
                    if (args[i] == null && methodParam.getAnnotation(NotNull.class) != null) {
                        throw new ServiceException(CoreExceptionDefinition.LAUNCHER_USER_NOT_LOGIN);
                    }
                } else if (httpParam.type() == HttpParamType.ADMIN_ID) {
                    String accessToken = request.getHeader(Const.ADMIN_ACCESS_TOKEN);
                    if (apiContext.entry == ApiEntry.WS && StringUtils.isEmpty(accessToken)) {
                        accessToken = request.getParameter(Const.ADMIN_ACCESS_TOKEN);
                    }
                    PermissionOwner adminDTO = adminAuthenticator.getAdmin(accessToken);
                    if (adminDTO != null) {
                        sessionUtil.setAdmin(adminDTO);
                        args[i] = adminDTO.getId();
                        personId = adminDTO.getId();
                        MDC.put("account", "ADMIN_" + personId);
                        MDC.put("token", accessToken);
                    }
                    if (args[i] == null && methodParam.getAnnotation(NotNull.class) != null) {
                        throw new ServiceException(CoreExceptionDefinition.LAUNCHER_ADMIN_NOT_LOGIN);
                    }
                } else if (httpParam.type() == HttpParamType.CUSTOM_ACCOUNT_ID) {
                    Class<?> clazz = httpParam.customAccountClass();
                    if (clazz == Object.class) {
                        throw new ServiceException(CoreExceptionDefinition.LAUNCHER_ADMIN_NOT_LOGIN);
                    }
                    String simpleName = clazz.getSimpleName();
                    String header = simpleName.replace("DO", "").replace("DTO", "");
                    String accessToken = request.getHeader(header.toUpperCase() + "TOKEN");
                    if (apiContext.entry == ApiEntry.WS && StringUtils.isEmpty(accessToken)) {
                        accessToken = request.getParameter(header.toUpperCase() + "TOKEN");
                    }
                    CustomAccountOwner custom = customAuthenticator.getCustom(clazz, accessToken);
                    if (custom != null) {
                        sessionUtil.setCustom(custom);
                        args[i] = custom.getId();
                        personId = custom.getId();
                        MDC.put("account", header.toUpperCase() + "_" + personId);
                        MDC.put("token", accessToken);
                    }
                    if (args[i] == null && methodParam.getAnnotation(NotNull.class) != null) {
                        throw new ServiceException(CoreExceptionDefinition.LAUNCHER_ADMIN_NOT_LOGIN);
                    }
                } else if (httpParam.type() == HttpParamType.IP) {
                    //这里根据实际情况来定。 若使用了负载均衡，Ip将会被代理服务器设置到某个Header里面
                    args[i] = ip;
                } else if (httpParam.type() == HttpParamType.HEADER) {
                    Class<?> type = methodParam.getType();
                    String header = request.getHeader(httpParam.name());
                    if (StringUtils.isNotEmpty(header) && Const.IGNORE_PARAM_LIST.contains(type)) {
                        Constructor<?> constructor = type.getConstructor(String.class);
                        args[i] = constructor.newInstance(header);
                    } else {
                        args[i] = header;
                    }
                } else if (httpParam.type() == HttpParamType.FILE) {
                    // 读文件
                    if (request instanceof MultipartHttpServletRequest multipartHttpServletRequest) {
                        MultipartFile file = multipartHttpServletRequest.getFile(httpParam.name());
                        if (file != null) {
                            try (InputStream inputStream = file.getInputStream()) {
                                byte[] bytes = StreamUtils.copyToByteArray(inputStream);
                                args[i] = bytes;
                            }
                        }
                    } else {
                        throw new ServiceException(CoreExceptionDefinition.LAUNCHER_READ_FILE_JUST_SUPPORT_MULTIPART);
                    }
                } else if (httpParam.type() == HttpParamType.FILE_NAME) {
                    if (request instanceof MultipartHttpServletRequest multipartHttpServletRequest) {
                        String name = httpParam.name();
                        MultipartFile file = multipartHttpServletRequest.getFile(httpParam.name().substring(0, name.length() - 4));
                        if (file != null) {
                            String originalFilename = file.getOriginalFilename();
                            args[i] = originalFilename;
                        }
                    } else {
                        throw new ServiceException(CoreExceptionDefinition.LAUNCHER_READ_FILE_JUST_SUPPORT_MULTIPART);
                    }
                } else if (httpParam.type() == HttpParamType.EXCEL) {
                    // 读Excel
                    if (request instanceof MultipartHttpServletRequest multipartHttpServletRequest) {
                        MultipartFile file = multipartHttpServletRequest.getFile(httpParam.name());
                        if (file != null) {
                            List<?> list = null;
                            try {
                                list = ExcelUtils.importExcel(file, httpParam.arrayClass());
                            } catch (RuntimeException e) {
                                logger.error("[导入Excel] 异常", e);
                                throw new ServiceException(e.getMessage());
                            }
                            args[i] = list;
                        }
                    } else {
                        throw new ServiceException(CoreExceptionDefinition.LAUNCHER_READ_FILE_JUST_SUPPORT_MULTIPART);
                    }
                }
                if (args[i] == null) {
                    NotNull annotation = methodParam.getAnnotation(NotNull.class);
                    if (args[i] == null && annotation != null) {
                        this.throwParamCheckServiceException(annotation);
                    }
                }
            }
            // 流量限制
            if (!this.rateLimiter.acquire(_gp + "." + _mt, httpMethod, personId, ip)) {
                throw new ServiceException(CoreExceptionDefinition.LAUNCHER_SYSTEM_BUSY);
            }
            ClassLoader classLoader = serviceBean.getClass().getClassLoader();
            Thread.currentThread().setContextClassLoader(classLoader);
            Object invokeObj = customInvoker.invoke(serviceBean, method, args);
            if (invokeObj instanceof SseEmitterWrapper || invokeObj instanceof SseEmitter || invokeObj instanceof WsWrapper) {
                // 直接返回
                return invokeObj;
            }
            String fileName = httpMethod.exportFileName();
            if (StringUtils.isNotEmpty(fileName)) {
                fileName = fileName.replace("${time}", new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
                String encodeFilename = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
                response.addHeader("Access-Control-Expose-Headers", "Content-Disposition");
                response.addHeader("Content-Disposition","attachment;filename="+encodeFilename);
            }
            GatewayResponse<Object> gatewayResponse = new GatewayResponse<>();
            gatewayResponse.setErrno(200);
            gatewayResponse.setErrmsg("成功");
            gatewayResponse.setContentType(httpMethod.contentType());
            gatewayResponse.setData(invokeObj);
            return GatewayResponse.success(invokeObj, httpMethod.contentType());
        } catch (ServiceException e) {
            if (e.getLogLevel() != null && e.getLogLevel() == Level.ERROR) {
                logger.error("[HTTP] Service exception stack top: {}", e.getStackTrace()[0].toString());
            } else {
                logger.info("[HTTP] Service exception stack top: {}", e.getStackTrace()[0].toString());
            }
            throw e;
        } catch (Exception e) {
            Throwable target = e;
            if (e instanceof InvocationTargetException proxy) {
                Throwable targetException = proxy.getTargetException();
                target = targetException;
                if (targetException instanceof ServiceException) {
                    logger.info("[HTTP] Service Stack Top: {}", targetException.getStackTrace()[0].toString());
                    throw (ServiceException) targetException;
                }
            }
            logger.error("[HTTP] R={}", apiContext.requestLogMap());
            Class<? extends Throwable> clazz = target.getClass();
            OtherExceptionTransfer transfer = otherExceptionTransferHolder.getByClass(clazz);
            ServiceException afterTransServiceException = transfer.trans(target);
            if (afterTransServiceException != null) {
                logger.error("[HTTP] 系统未知异常 message={}", afterTransServiceException.getMessage(), e);
                throw afterTransServiceException;
            }
            logger.error("[HTTP] 系统未知异常", e);
            throw new ServiceException(CoreExceptionDefinition.LAUNCHER_UNKNOWN_EXCEPTION);
        }
    }



    private void throwParamCheckServiceException(Annotation annotation) throws ServiceException {
        try {
            Method method = annotation.getClass().getMethod("message");
            Object res = method.invoke(annotation);
            if (!ObjectUtils.isEmpty(res)) {
                throw new ServiceException((String) res, CoreExceptionDefinition.LAUNCHER_PARAM_CHECK_FAILED.getCode());
            } else {
                throw new ServiceException(CoreExceptionDefinition.LAUNCHER_PARAM_CHECK_FAILED);
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        }
    }

    private String buildServiceResult(HttpServletResponse res, long invokeTime, ServiceException e) {
        GatewayResponse<Object> gatewayResponse = new GatewayResponse<>();
        gatewayResponse.setTimestamp(invokeTime);
        gatewayResponse.setErrno(e.getCode());
        gatewayResponse.setErrmsg(e.getMessage());
        gatewayResponse.setData(e.getAttach());
        return afterPost(res, invokeTime, gatewayResponse, true, false);
    }

}

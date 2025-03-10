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
import com.dobbinsoft.fw.launcher.inter.BeforeProcess;
import com.dobbinsoft.fw.launcher.invoker.CustomInvoker;
import com.dobbinsoft.fw.launcher.manager.IApiManager;
import com.dobbinsoft.fw.launcher.permission.IAdminAuthenticator;
import com.dobbinsoft.fw.launcher.permission.ICustomAuthenticator;
import com.dobbinsoft.fw.launcher.permission.IUserAuthenticator;
import com.dobbinsoft.fw.support.model.WsWrapper;
import com.dobbinsoft.fw.support.rate.RateLimiter;
import com.dobbinsoft.fw.support.rpc.RpcContextHolder;
import com.dobbinsoft.fw.support.rpc.RpcProviderUtils;
import com.dobbinsoft.fw.support.utils.*;
import com.dobbinsoft.fw.support.utils.excel.ExcelBigExportAdapter;
import com.dobbinsoft.fw.support.utils.excel.ExcelData;
import com.dobbinsoft.fw.support.utils.excel.ExcelUtils;
import com.dobbinsoft.fw.support.ws.WsPublisher;
import com.dobbinsoft.fw.support.ws.event.WsEventReceiver;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
import java.util.regex.Pattern;

/**
 * API Controller，RPC/WEB 调用流量入口。统一的方法路由，参数校验等逻辑。
 */
@RestController
@RequestMapping("/")
public class ApiController {

    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private BeforeProcess beforeProcess;

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
    private WsPublisher wsPublisher;

    @Autowired(required = false)
    private WsEventReceiver wsEventReceiver;

    @Autowired
    private IApiManager apiManager;

    private static final String WS_CURRENT_IDENTITY_OWNER_KEY = "WS_CURRENT_IDENTITY_OWNER_KEY";

    private static final Pattern URI_PATTERN = Pattern.compile("/ws\\.api/([^/]+)/([^/]+)");

//    @Override
//    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
//        if (wsPublisher != null) {
//            registry.addHandler(this, "/ws.api", "/ws.api/{_gp}/{_mt}")
//                    .setAllowedOrigins("*")
//                    .addInterceptors(this);  // 添加拦截器进行认证
//        }
//    }

//    @Override
//    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//        Object o = session.getAttributes().get(WS_CURRENT_IDENTITY_OWNER_KEY);
//        logger.info("[WS] New Session created, session id: {}", session.getId());
//        String identityOwnerKey = o.toString();
//        wsPublisher.join(identityOwnerKey, session);
//    }

//    @Override
//    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
//        Object o = session.getAttributes().get(WS_CURRENT_IDENTITY_OWNER_KEY);
//        logger.info("[WS] Session closed, session id: {}", session.getId());
//        String identityOwnerKey = o.toString();
//        wsPublisher.quit(identityOwnerKey);
//    }

//    @Override
//    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
//        Object payload = message.getPayload();
//        if (payload instanceof String) {
//            String event = payload.toString();
//            JsonNode jsonNode = JacksonUtil.parseObject(event);
//            JsonNode eventTypeObj = jsonNode.get("eventType");
//            if (eventTypeObj == null) {
//                logger.info("[WS] Event 报文格式不正确 event: {}", event);
//            } else {
//                String eventType = eventTypeObj.asText();
//                wsEventReceiver.route(eventType, event);
//            }
//        } else {
//            logger.info("[WS] Session 仅支持text报文, session id: {}", session.getId());
//        }
//    }

//    @Override
//    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
//        if (wsPublisher == null) {
//            logger.info("[WS] 请使用@EnableWs主机 激活Websocket功能");
//            return false;
//        }
//        long invokeTime = System.currentTimeMillis();
//        HttpServletRequest req;
//        if (request instanceof ServletServerHttpRequest) {
//            req = ((ServletServerHttpRequest) request).getServletRequest();
//        } else {
//            logger.warn("[WS] 目前仅支持Http握手");
//            return false;
//        }
//        HttpServletResponse res;
//        if (response instanceof ServletServerHttpResponse) {
//            res = ((ServletServerHttpResponse) response).getServletResponse();
//        } else {
//            logger.warn("[WS] 目前仅支持Http握手");
//            return false;
//        }
//
//        String requestURI = req.getRequestURI();
//        if (serverProperties.getServlet() != null &&
//                StringUtils.isNotEmpty(serverProperties.getServlet().getContextPath())) {
//            requestURI = requestURI.substring(serverProperties.getServlet().getContextPath().length());
//        }
//        // 解析路径参数
//        Matcher matcher = URI_PATTERN.matcher(requestURI);
//        String _gp = null;
//        String _mt = null;
//        if (matcher.matches()) {
//            // 提取路径参数
//            _gp = matcher.group(1); // 第一个路径参数
//            _mt = matcher.group(2); // 第二个路径参数
//        }
//        ApiContext context = findContext(req, _gp, _mt, ApiEntry.WS);
//        try {
//            Object obj = process(req, res, context);
//            if (obj instanceof WsWrapper) {
//                // 鉴权 & 握手完成， 后续进入afterConnectionEstablished方法
//                attributes.put(WS_CURRENT_IDENTITY_OWNER_KEY, ((WsWrapper) obj).getIdentityOwnerKey());
//                return true;
//            }
//            logger.error("[WS] 方法返回对象并非 WsWrapper");
//            return false;
//        } catch (ServiceException e) {
//            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
//            byte[] result = buildServiceResult(res, invokeTime, e).getBytes(StandardCharsets.UTF_8);
//            try (OutputStream os = res.getOutputStream()) {
//                os.write(result);
//            }
//        } finally {
//            MDC.clear();
//            sessionUtil.clear();
//        }
//        return false;
//    }


    /**
     * 远程调用时 接口地址， 通常nginx配置时不暴露
     *
     * @param exchange
     * @param _gp
     * @param _mt
     * @throws IOException
     */
    @RequestMapping(value = {"/rpc", "/rpc/{_gp}/{_mt}"}, method = {RequestMethod.POST, RequestMethod.GET})
    public Mono<ResponseEntity<byte[]>> rpcInvoke(
            ServerWebExchange exchange,
            @PathVariable(required = false) String _gp,
            @PathVariable(required = false) String _mt) throws IOException {
        try {
            HttpHeaders headers = exchange.getRequest().getHeaders();
            // Valid rpc token
            String jwtToken = RequestUtils.getHeaderValue(headers, Const.RPC_HEADER);
            String systemId = RequestUtils.getHeaderValue(headers, Const.RPC_SYSTEM_ID);
            if (rpcProviderUtils == null) {
                logger.error("[RPC] You need to add @EnableRpc");
            }
            JwtUtils.JwtResult jwtResult = rpcProviderUtils.validToken(systemId, jwtToken);
            if (jwtResult.getResult() != JwtUtils.Result.SUCCESS) {
                throw new ServiceException(CoreExceptionDefinition.LAUNCHER_RPC_SIGN_INCORRECT);
            }
            String rpcContextJson = RequestUtils.getHeaderValue(headers, Const.RPC_CONTEXT_JSON);
            if (StringUtils.isNotEmpty(rpcContextJson)) {
                Map<String, String> rpcContexts = JacksonUtil.parseObject(rpcContextJson, new TypeReference<Map<String, String>>() {
                });
                rpcContexts.forEach(RpcContextHolder::add);
            }
            return commonsInvoke(exchange, _gp, _mt, ApiEntry.RPC);
        } catch (ServiceException e) {
            byte[] result = buildServiceResult(exchange, System.currentTimeMillis(), e).getBytes(StandardCharsets.UTF_8);
            return Mono.just(ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result));
        }

    }

    /**
     * sse 请求时地址，返回的content-type为text/event-stream
     *
     * @param exchange
     * @param _gp
     * @param _mt
     * @return
     */
    @RequestMapping(
            value = {"/sse.api", "/sse.api/{_gp}/{_mt}"},
            method = {RequestMethod.POST, RequestMethod.GET},
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<?> sseInvoke(
            ServerWebExchange exchange,
            @PathVariable(required = false) String _gp,
            @PathVariable(required = false) String _mt,
            @RequestParam(required = false, name = "_gp") String _gpParam,
            @RequestParam(required = false, name = "_mt") String _mtParam) {
        // 获取请求上下文
        return this.findContext(exchange, StringUtils.firstNonEmpty(_gp, _gpParam), StringUtils.firstNonEmpty(_mt, _mtParam), ApiEntry.SSE)
                .flatMapMany(context -> {
                    // 如果上下文中有异常，直接返回错误
                    if (context.getServiceException() != null) {
                        return Flux.error(context.getServiceException());
                    }
                    try {
                        Object result = processSync(exchange, context);
                        if (result instanceof Flux<?>) {
                            return (Flux<?>) result;
                        } else {
                            // 如果不是 Flux，返回错误
                            return Flux.error(new ServiceException(CoreExceptionDefinition.LAUNCHER_SSE_ONLY_RETURN_FLUX));
                        }
                    } catch (ServiceException e) {
                        // 捕获 ServiceException 并返回错误
                        return Flux.error(e);
                    } catch (Exception e) {
                        // 捕获其他异常并返回错误
                        logger.error("[SSE 系统未知异常]", e);
                        return Flux.error(new ServiceException(CoreExceptionDefinition.LAUNCHER_UNKNOWN_EXCEPTION));
                    }
                })
                .onErrorResume(e -> {
                    // 全局错误处理
                    if (e instanceof ServiceException) {
                        return Flux.error(e);
                    } else {
                        return Flux.error(new ServiceException(CoreExceptionDefinition.LAUNCHER_UNKNOWN_EXCEPTION));
                    }
                });
    }

    /**
     * 普通请求
     *
     * @param exchange
     * @param _gp
     * @param _mt
     * @throws IOException
     */
    @RequestMapping(value = {"/m.api", "/m.api/{_gp}/{_mt}"}, method = {RequestMethod.POST, RequestMethod.GET})
    public Mono<ResponseEntity<byte[]>> invoke(
            ServerWebExchange exchange,
            @PathVariable(required = false) String _gp,
            @PathVariable(required = false) String _mt) throws IOException {
        return commonsInvoke(exchange, _gp, _mt, ApiEntry.WEB);
    }


    private Mono<ResponseEntity<byte[]>> commonsInvoke(ServerWebExchange exchange, String _gp, String _mt, ApiEntry apiEntry) throws IOException {
        long invokeTime = System.currentTimeMillis();
        // 请求调用上下文
        Mono<ApiContext> contextMono = this.findContext(exchange, _gp, _mt, apiEntry);
        // 执行请求
        return contextMono.flatMap(context -> {
            Map<String, String> headerMap = new HashMap<>();
            if (context.getServiceException() != null) {
                return Mono.error(context.getServiceException());
            }
            Object obj;
            try {
                obj = processSync(exchange, context);
            } catch (ServiceException e) {
                return Mono.error(e);
            }
            byte[] result;
            MediaType contentType = MediaType.APPLICATION_JSON;
            try {
                if (Const.IGNORE_PARAM_LIST.contains(obj.getClass())) {
                    contentType = MediaType.TEXT_HTML;
                    result = obj.toString().getBytes(StandardCharsets.UTF_8);
                } else if (context.httpExcel != null && obj instanceof GatewayResponse<?> gatewayResponse) {
                    contentType = new MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    // 直接输出文件
                    ExcelData<?> excelData = new ExcelData<>();
                    Object respData = gatewayResponse.getData();
                    // 设置文件名
                    String fileName = context.httpExcel.fileName() + "-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
                    headerMap.put("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
                    if (respData instanceof List<?>) {
                        List data = (List<?>) respData;
                        excelData.setData(data);
                        excelData.setFileName(fileName);
                        result = ExcelUtils.exportExcel(excelData.getData(), context.httpExcel.clazz());
                    } else if (respData instanceof ExcelBigExportAdapter<?> excelBigExportAdapter) {
                        result = ExcelUtils.exportBigExcel(excelBigExportAdapter);
                    } else {
                        return Mono.error(new RuntimeException("Http Excel 只能返回List或者ExcelBigExportAdapter"));
                    }
                    afterPost(exchange, invokeTime, obj, false, context.httpMethod.noLog());
                } else if (obj instanceof GatewayResponse<?> gatewayResponse) {
                    gatewayResponse.setTimestamp(invokeTime);
                    if (gatewayResponse.getContentType() != null) {
                        contentType = gatewayResponse.getContentType();
                    }
                    if (CollectionUtils.isNotEmpty(gatewayResponse.getHttpHeaders())) {
                        headerMap.putAll(gatewayResponse.getHttpHeaders());
                    }
                    Object data = gatewayResponse.getData();
                    if (data instanceof byte[]) {
                        result = (byte[]) data;
                        afterPost(exchange, invokeTime, obj, false, context.httpMethod.noLog());
                    } else {
                        result = afterPost(exchange, invokeTime, obj, true, context.httpMethod.noLog()).getBytes(StandardCharsets.UTF_8);
                    }
                } else if (obj instanceof Flux<?>) {
                    // SSE
                    result = afterPost(exchange, invokeTime, obj, false, context.httpMethod.noLog()).getBytes(StandardCharsets.UTF_8);
                } else {
                    result = afterPost(exchange, invokeTime, obj, true, context.httpMethod.noLog()).getBytes(StandardCharsets.UTF_8);
                }
            } finally {
                MDC.clear();
                sessionUtil.clear();
            }
            ResponseEntity.BodyBuilder builder = ResponseEntity
                    .ok();
            // 添加自定义Header
            headerMap.forEach(builder::header);
            ResponseEntity<byte[]> body = builder
                    .contentType(contentType)
                    .body(result);
            return Mono.just(body);

        }).onErrorResume(e -> {
            byte[] result;
            if (e instanceof ServiceException) {
                result = buildServiceResult(exchange, invokeTime, (ServiceException) e).getBytes(StandardCharsets.UTF_8);
            } else {
                // 未知异常
                logger.error("[HTTP] 系统未知异常", e);
                result = buildServiceResult(exchange, invokeTime, new ServiceException(CoreExceptionDefinition.LAUNCHER_UNKNOWN_EXCEPTION)).getBytes(StandardCharsets.UTF_8);
            }

            return Mono.just(ResponseEntity
                    .ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result));
        });


    }

    /**
     * 后置通知 抽取方法
     *
     * @param exchange   请求
     * @param invokeTime 调用开始时间
     * @param obj        调用结果
     * @return
     */
    private String afterPost(ServerWebExchange exchange, long invokeTime, Object obj, boolean json, boolean noLog) {
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
            afterHttpMethod.after(exchange, result);
        }
        return result;
    }


    /**
     * 获取请求上下文
     *
     * @param exchange
     * @param _gp      已知晓_gp
     * @param _mt      已知晓_mt
     * @param apiEntry API入口，决定分组
     * @return
     * @throws ServiceException 服务异常
     */
    private Mono<ApiContext> findContext(ServerWebExchange exchange, String _gp, String _mt, ApiEntry apiEntry) {

        // 如果_gp,_mt已经预知，那么我们就没必要从流里面读取 _gp,_mt。也就是读请求报文流和获取session这两个IO操作可并行执行。所以在webflux版本中，我们强制要求_gp, _mt 两个字段必须传在URL中。
        if (StringUtils.isEmpty(_gp) || StringUtils.isEmpty(_mt)) {
            ApiContext apiContext = new ApiContext();
            apiContext.setServiceException(new ServiceException(CoreExceptionDefinition.LAUNCHER_API_NOT_EXISTS));
            return Mono.just(apiContext);
        }
        Mono<Void> before = Mono.empty();
        if (beforeProcess != null) {
            before = beforeProcess.before(exchange);
        }
        return before.then(Mono.defer(() -> {
            HttpHeaders headers = exchange.getRequest().getHeaders();
            Method method = apiEntry == ApiEntry.RPC ? apiManager.getRpcMethod(_gp, _mt) : apiManager.getMethod(_gp, _mt);

            // 1. 获取Context
            Mono<ApiContext> contextMono = ApiContext.getApiContextMono(exchange, headers, method);

            // 2. 尝试获取Session
            Parameter[] parameters = method.getParameters();
            Mono<? extends IdentityOwner> identityMono = null;
            HttpParamType identityType = null;

            try {
                for (Parameter parameter : parameters) {
                    HttpParam httpParam = parameter.getAnnotation(HttpParam.class);
                    if (httpParam.type() == HttpParamType.ADMIN_ID) {
                        String accessToken = RequestUtils.getHeaderValue(headers, Const.ADMIN_ACCESS_TOKEN);
                        identityMono = adminAuthenticator.getAdmin(accessToken);
                        identityType = HttpParamType.ADMIN_ID;
                        break;
                    } else if (httpParam.type() == HttpParamType.USER_ID) {
                        String accessToken = RequestUtils.getHeaderValue(headers, Const.USER_ACCESS_TOKEN);
                        identityMono = userAuthenticator.getUser(accessToken);
                        identityType = HttpParamType.USER_ID;
                        break;
                    } else if (httpParam.type() == HttpParamType.CUSTOM_ACCOUNT_ID) {
                        Class clazz = httpParam.customAccountClass();
                        String simpleName = clazz.getSimpleName();
                        String header = simpleName.replace("DO", "").replace("DTO", "");
                        String accessToken = RequestUtils.getHeaderValue(headers, header.toUpperCase() + "TOKEN");
                        identityMono = customAuthenticator.getCustom(clazz, accessToken);
                        identityType = HttpParamType.CUSTOM_ACCOUNT_ID;
                        break;
                    }
                }
            } catch (ServiceException e) {
                ApiContext apiContext = new ApiContext();
                apiContext.setServiceException(e);
                return Mono.just(apiContext);
            }

            if (identityMono == null) {
                // 无需登录的开放接口，则不需要等待读取redis
                return contextMono.map(apiContext -> zip(apiEntry, null, apiContext, null));
            }

            HttpParamType finalIdentityType = identityType;
            return Mono.zip(contextMono, identityMono)
                    .switchIfEmpty(Mono.defer(() -> {
                        switch (finalIdentityType) {
                            case ADMIN_ID, CUSTOM_ACCOUNT_ID -> {
                                return Mono.error(new ServiceException(CoreExceptionDefinition.LAUNCHER_ADMIN_NOT_LOGIN));
                            }
                            case USER_ID -> {
                                return Mono.error(new ServiceException(CoreExceptionDefinition.LAUNCHER_USER_NOT_LOGIN));
                            }
                        }
                        return Mono.empty();
                    }))
                    .flatMap(tuple -> {
                ApiContext apiContext = tuple.getT1();
                if (apiContext.getServiceException() != null) {
                    return Mono.just(apiContext);
                }
                IdentityOwner identityOwner = tuple.getT2();
                // 封装zip之后的apiContext信息
                zip(apiEntry, finalIdentityType, apiContext, identityOwner);
                return Mono.just(apiContext);
            });
        }));
    }

    private static ApiContext zip(ApiEntry apiEntry, HttpParamType finalIdentityType, ApiContext apiContext, IdentityOwner identityOwner) {
        if (finalIdentityType != null) {
            switch (finalIdentityType) {
                case ADMIN_ID -> apiContext.setAdmin((PermissionOwner) identityOwner);
                case USER_ID -> apiContext.setUser(identityOwner);
                case CUSTOM_ACCOUNT_ID -> apiContext.setCustom((CustomAccountOwner) identityOwner);
            }
        }
        apiContext.httpMethod = apiContext.method.getAnnotation(HttpMethod.class);
        if (apiContext.httpMethod == null) {
            //只起标记作用防止调到封闭方法了
            apiContext.setServiceException(new ServiceException(CoreExceptionDefinition.LAUNCHER_API_NOT_EXISTS));
            return apiContext;
        }
        apiContext.httpExcel = apiContext.method.getAnnotation(HttpExcel.class);
        // 判断API URL是否正确
        if ((apiContext.method.getReturnType() == Flux.class) && apiEntry != ApiEntry.SSE) {
            apiContext.setServiceException(new ServiceException(CoreExceptionDefinition.LAUNCHER_ONLY_SSE_SUPPORT));
            return apiContext;
        }
        if (apiContext.method.getReturnType() == WsWrapper.class && apiEntry != ApiEntry.WS) {
            apiContext.setServiceException(new ServiceException(CoreExceptionDefinition.LAUNCHER_ONLY_WS_SUPPORT));
            return apiContext;
        }
        return apiContext;
    }

    /**
     * 调用HttpMethod
     *
     * @param exchange
     * @param apiContext
     * @return 返回HttpMethod原始返回
     * @throws ServiceException
     */
    private Object processSync(ServerWebExchange exchange, ApiContext apiContext) throws ServiceException {
        try {
            IApiManager apiManager = applicationContext.getBean(IApiManager.class);
            Method method = apiContext.method;
            String _gp = apiContext._gp;
            String _mt = apiContext._mt;
            HttpMethod httpMethod = apiContext.httpMethod;

            logger.info("[HTTP] Q={}", JacksonUtil.toJSONString(apiContext.getParameterMap()));
            HttpHeaders headers = exchange.getRequest().getHeaders();
            String permission = httpMethod.permission();
            if (StringUtils.isNotEmpty(permission)) {
                //若需要权限，则校验当前用户是否具有权限
                PermissionOwner adminDTO = apiContext.getAdmin();
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
            String ip = RequestUtils.getClientIp(exchange.getRequest());
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
                    String accessToken = RequestUtils.getHeaderValue(headers, Const.USER_ACCESS_TOKEN);
                    IdentityOwner user = apiContext.getUser();
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
                    String accessToken = RequestUtils.getHeaderValue(headers, Const.ADMIN_ACCESS_TOKEN);
                    PermissionOwner adminDTO = apiContext.getAdmin();
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
                    String accessToken = RequestUtils.getHeaderValue(headers, header.toUpperCase() + "TOKEN");
                    CustomAccountOwner custom = apiContext.getCustom();
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
                    String header = RequestUtils.getHeaderValue(headers, httpParam.name());
                    if (StringUtils.isNotEmpty(header) && Const.IGNORE_PARAM_LIST.contains(type)) {
                        Constructor<?> constructor = type.getConstructor(String.class);
                        args[i] = constructor.newInstance(header);
                    } else {
                        args[i] = header;
                    }
                } else if (httpParam.type() == HttpParamType.FILE) {
                    // 读文件
                    Map<String, byte[]> fileMap = apiContext.getFileMap();
                    if (CollectionUtils.isEmpty(fileMap)) {
                        throw new ServiceException(CoreExceptionDefinition.PARAM_CHECK_FAILED);
                    }
                    byte[] bytes = fileMap.get(httpParam.name());
                    args[i] = bytes;
                } else if (httpParam.type() == HttpParamType.FILE_NAME) {
                    String fileName = apiContext.getParameter(httpParam.name());
                    args[i] = fileName;
                } else if (httpParam.type() == HttpParamType.EXCEL) {
                    // 读Excel
                    Map<String, byte[]> fileMap = apiContext.getFileMap();
                    if (CollectionUtils.isEmpty(fileMap)) {
                        throw new ServiceException(CoreExceptionDefinition.PARAM_CHECK_FAILED);
                    }
                    byte[] bytes = fileMap.get(httpParam.name());
                    if (bytes != null) {
                        try {
                            String fileName = apiContext.getParameter(httpParam.name());
                            args[i] = ExcelUtils.importExcel(new ByteArrayInputStream(bytes), fileName, httpParam.arrayClass());
                        } catch (RuntimeException e) {
                            logger.error("[导入Excel] 异常", e);
                            throw new ServiceException(e.getMessage());
                        }
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
            if (invokeObj instanceof Flux<?> || invokeObj instanceof WsWrapper) {
                // 直接返回SSE 或 WS
                return invokeObj;
            }
            GatewayResponse<Object> gatewayResponse = new GatewayResponse<>();
            String fileName = httpMethod.exportFileName();
            if (StringUtils.isNotEmpty(fileName)) {
                fileName = fileName.replace("${time}", new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
                String encodeFilename = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
                Map<String, String> httpHeaders = new HashMap<>();
                httpHeaders.put("Access-Control-Expose-Headers", "Content-Disposition");
                httpHeaders.put("Content-Disposition", "attachment;filename=" + encodeFilename);
                gatewayResponse.setHttpHeaders(httpHeaders);
            }
            gatewayResponse.setErrno(200);
            gatewayResponse.setErrmsg("成功");
            if (StringUtils.isNotEmpty(httpMethod.contentType())) {
                gatewayResponse.setContentType(MediaType.parseMediaType(httpMethod.contentType()));
            }
            gatewayResponse.setData(invokeObj);
            return gatewayResponse;
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

    private String buildServiceResult(ServerWebExchange exchange, long invokeTime, ServiceException e) {
        GatewayResponse<Object> gatewayResponse = new GatewayResponse<>();
        gatewayResponse.setTimestamp(invokeTime);
        gatewayResponse.setErrno(e.getCode());
        gatewayResponse.setErrmsg(e.getMessage());
        gatewayResponse.setData(e.getAttach());
        return afterPost(exchange, invokeTime, gatewayResponse, true, false);
    }

}

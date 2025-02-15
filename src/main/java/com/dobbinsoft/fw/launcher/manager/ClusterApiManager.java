package com.dobbinsoft.fw.launcher.manager;

import com.dobbinsoft.fw.core.annotation.*;
import com.dobbinsoft.fw.core.annotation.doc.ApiEntity;
import com.dobbinsoft.fw.core.annotation.doc.ApiField;
import com.dobbinsoft.fw.core.annotation.doc.ApiLog;
import com.dobbinsoft.fw.core.annotation.param.NotNull;
import com.dobbinsoft.fw.core.enums.BaseEnums;
import com.dobbinsoft.fw.core.enums.EmptyEnums;
import com.dobbinsoft.fw.core.exception.CoreExceptionDefinition;
import com.dobbinsoft.fw.core.exception.ServiceException;
import com.dobbinsoft.fw.core.model.GatewayResponse;
import com.dobbinsoft.fw.launcher.inter.AfterRegisterApiComplete;
import com.dobbinsoft.fw.support.model.PermissionPoint;
import com.dobbinsoft.fw.support.utils.StringUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created with IntelliJ IDEA.
 * Description: 集群版 ApiManager 实现
 * User: rize
 * Date: 2018-08-08
 * Time: 下午10:52
 */
@Component
public class ClusterApiManager implements InitializingBean, ApplicationContextAware, IApiManager {

    private static final Logger logger = LoggerFactory.getLogger(ClusterApiManager.class);

    private final Map<String, Map<String, Method>> methodCacheMap = new HashMap<>();

    private final Map<String, Map<String, Method>> rpcMethodCacheMap = new HashMap<>();

    private final Map<String, String> groupDescCacheMap = new HashMap<>();

    private ApplicationContext applicationContext;

    private final List<PermissionPoint> permDTOs = new LinkedList<>();

    private final Set<Class> interfaceClassList = new TreeSet<>(Comparator.comparing(Class::getName));

    private final Map<String, String> permissionRouteMap = new HashMap<>();

    private OpenAPI openApiCache = null;


    @Autowired(required = false)
    private AfterRegisterApiComplete afterRegisterApiComplete;

    @Override
    public void afterPropertiesSet() throws Exception {
        List<Class> classList = new LinkedList<>();
        List<Class> rpcClassList = new LinkedList<>();
        String[] beanArray = applicationContext.getBeanNamesForAnnotation(Service.class);
        for (String service : beanArray) {
            Object bean = applicationContext.getBean(service);
            String className = bean.toString().split("@")[0];
            Class<?> serviceClazz = Class.forName(className);
            Class<?>[] interfaces = serviceClazz.getInterfaces();
            for (Class clazz : interfaces) {
                if (clazz.getAnnotation(HttpOpenApi.class) != null) {
                    classList.add(clazz);
                } else if (clazz.getAnnotation(RpcService.class) != null) {
                    rpcClassList.add(clazz);
                }
            }
        }
        for (Class clazz : classList) {
            this.registerService(clazz);
        }
        for (Class clazz : rpcClassList) {
            this.registerRpcService(clazz);
        }
        if (afterRegisterApiComplete != null) {
            afterRegisterApiComplete.after(permDTOs);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private void registerService(Class<?> targetClass) throws ServiceException {
        HttpOpenApi httpOpenApiAnnotation = targetClass.getDeclaredAnnotation(HttpOpenApi.class);
        if (httpOpenApiAnnotation != null) {
            interfaceClassList.add(targetClass);
            String group = httpOpenApiAnnotation.group();
            Method[] methods = targetClass.getMethods();
            Map<String, Method> tempMap = methodCacheMap.get(group);
            if (tempMap == null) {
                tempMap = new TreeMap<>();
                methodCacheMap.put(group, tempMap);
                groupDescCacheMap.put(group, httpOpenApiAnnotation.description());
            }
            for (Method method : methods) {
                HttpMethod httpMethod = method.getAnnotation(HttpMethod.class);
                if (httpMethod != null) {
                    String permission = httpMethod.permission();
                    if (StringUtils.isNotEmpty(permission)) {
                        //若此接口需要权限
                        if (StringUtils.isEmpty(httpMethod.permissionParentName()) || StringUtils.isEmpty(httpMethod.permissionName())) {
                            throw new ServiceException(CoreExceptionDefinition.LAUNCHER_API_REGISTER_FAILED);
                        }
                        //迭代已有接口
                        boolean hasParent = false;
                        permDTOLoop:
                        for (PermissionPoint pointDTO : permDTOs) {
                            if (pointDTO.getLabel().equals(httpMethod.permissionParentName())) {
                                //若已经存在父分组，则将其追加在后面即可
                                hasParent = true;
                                addChildPermissionPoint(pointDTO, httpMethod, httpOpenApiAnnotation, method);
                                break permDTOLoop;
                            }
                        }
                        if (!hasParent) {
                            //若不存在父分组，则新建父分组
                            PermissionPoint parentDTO = new PermissionPoint();
                            parentDTO.setLabel(httpMethod.permissionParentName());
                            parentDTO.setId(httpMethod.permissionParentName());
                            parentDTO.setChildren(new LinkedList<>());
                            permDTOs.add(parentDTO);
                            //然后在parentDTO后面添加子类目，以及孙类目
                            addChildPermissionPoint(parentDTO, httpMethod, httpOpenApiAnnotation, method);
                        }
                    }
                    String key = method.getName();
                    Method methodQuery = tempMap.get(key);
                    if (methodQuery != null) {
                        logger.error("[注册OpenApi] Http Api不支持重载");
                        throw new ServiceException(CoreExceptionDefinition.LAUNCHER_API_REGISTER_FAILED);
                    }
                    tempMap.put(key, method);
                    logger.info("[注册OpenApi] " + group + "." + method.getName());
                } else {
                    logger.info("[注册OpenApi] 失败 没有注解." + method.getName());
                }

            }
        } else {
            throw new ServiceException(CoreExceptionDefinition.LAUNCHER_API_REGISTER_FAILED);
        }
    }


    private void registerRpcService(Class<?> targetClass) throws ServiceException {
        RpcService rpcService = targetClass.getDeclaredAnnotation(RpcService.class);
        if (rpcService != null) {
            interfaceClassList.add(targetClass);
            String group = rpcService.group();
            Method[] methods = targetClass.getMethods();
            Map<String, Method> tempMap = rpcMethodCacheMap.computeIfAbsent(group, k -> new TreeMap<>());
            for (Method method : methods) {
                HttpMethod httpMethod = method.getAnnotation(HttpMethod.class);
                if (httpMethod != null) {
                    String key = method.getName();
                    Method methodQuery = tempMap.get(key);
                    if (methodQuery != null) {
                        throw new ServiceException(CoreExceptionDefinition.LAUNCHER_API_REGISTER_FAILED);
                    }
                    tempMap.put(key, method);
                    logger.info("[注册RpcService] " + group + "." + method.getName());
                } else {
                    logger.info("[注册RpcService] 失败 没有注解." + method.getName());
                }

            }
        } else {
            throw new ServiceException(CoreExceptionDefinition.LAUNCHER_API_REGISTER_FAILED);
        }
    }


    private void addChildPermissionPoint(PermissionPoint parentDTO, HttpMethod httpMethod, HttpOpenApi httpOpenApi, Method method) throws ServiceException {
        if (CollectionUtils.isEmpty(parentDTO.getChildren())) {
            parentDTO.setChildren(new LinkedList<>());
        }

        boolean hasChild = false;

        for (PermissionPoint childDTO : parentDTO.getChildren()) {
            if (childDTO.getLabel().equals(httpMethod.permissionName())) {
                //若存在
                hasChild = true;
                addGrandChildPermissionPoint(childDTO, httpMethod, httpOpenApi, method);
            }
        }
        if (!hasChild) {
            //添加child
            PermissionPoint childDTO = new PermissionPoint();
            childDTO.setId(httpMethod.permissionName());
            childDTO.setLabel(httpMethod.permissionName());
            childDTO.setChildren(new LinkedList<>());
            parentDTO.getChildren().add(childDTO);
            addGrandChildPermissionPoint(childDTO, httpMethod, httpOpenApi, method);
        }


    }

    private void addGrandChildPermissionPoint(PermissionPoint childDTO, HttpMethod httpMethod, HttpOpenApi httpOpenApi, Method method) throws ServiceException {
        //遍历权限点是否有重复
        for (PermissionPoint pointDTO : childDTO.getChildren()) {
            if (pointDTO.getId().equals(httpMethod.permission())) {
                //不允许重复权限点
//                throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_API_REGISTER_FAILED);
                return;
            }
        }
        //若无重复权限点
        PermissionPoint pointDTO = new PermissionPoint();
        permissionRouteMap.put(httpMethod.permission(), httpMethod.permissionParentName() + "-" + httpMethod.permissionName() + "-" + httpMethod.description());
        pointDTO.setId(httpMethod.permission());
        pointDTO.setLabel(httpMethod.description());
        pointDTO.setApi(httpOpenApi.group() + "." + method.getName());
        childDTO.getChildren().add(pointDTO);
    }

    @Override
    public Method getMethod(String group, String name) {
        Map<String, Method> tempMap = methodCacheMap.get(group);
        if (tempMap != null) {
            return tempMap.get(name);
        }
        return null;
    }

    @Override
    public Method getRpcMethod(String group, String name) {
        Map<String, Method> tempMap = rpcMethodCacheMap.get(group);
        if (tempMap != null) {
            return tempMap.get(name);
        }
        return null;
    }

    @Override
    public Object getServiceBean(Method method) {
        return applicationContext.getBean(method.getDeclaringClass());
    }

    @Override
    public Set<Class> getRegisteredInterfaces() {
        return this.interfaceClassList;
    }

    @Override
    public String getPermissionRoute(String permission) {
        return permissionRouteMap.get(permission);
    }

    @Override
    public List<PermissionPoint> getPermissions() {
        return this.permDTOs;
    }


    public OpenAPI generateOpenApiModel() {
        if (openApiCache != null) {
            return openApiCache;
        }
        OpenAPI openAPI = new OpenAPI();
        Set<String> gpKeys = methodCacheMap.keySet();

        List<Tag> tags = gpKeys.stream().map(gpKey -> {
            Tag tag = new Tag();
            tag.setName(groupDescCacheMap.getOrDefault(gpKey, ""));
            tag.setDescription(gpKey);
            return tag;
        }).collect(Collectors.toList());
        openAPI.setOpenapi("3.0.1");
        openAPI.setInfo(new Info());
        openAPI.getInfo().setTitle("dobbinfw-v3");
        openAPI.getInfo().setDescription("道宾云（重庆）提供框架支持");
        openAPI.getInfo().setVersion("2.x");

        openAPI.setTags(tags);
        Paths paths = new Paths();
        openAPI.setPaths(paths);

        for (String gpKey : gpKeys) {
            String groupDescription = groupDescCacheMap.getOrDefault(gpKey, "");
            Map<String, Method> methodMap = methodCacheMap.get(gpKey);
            Set<String> methodNameKeys = methodMap.keySet();
            for (String methodNameKey : methodNameKeys) {
                PathItem pathItem = new PathItem();
                Operation postOperation = new Operation();
                pathItem.setPost(postOperation);
                Method method = methodMap.get(methodNameKey);
                HttpMethod httpMethod = method.getAnnotation(HttpMethod.class);
                if (httpMethod == null) {
                    continue;
                }
                //获取参数
                postOperation.setSummary(httpMethod.description());
                postOperation.setTags(Arrays.asList(groupDescription));
                postOperation.setDeprecated(false);
                // 更新日志
                ApiLog apiLog = method.getAnnotation(ApiLog.class);
                StringBuilder descriptionBuilder = new StringBuilder();
                if (StringUtils.isNotEmpty(httpMethod.permission())) {
                    descriptionBuilder.append("- 权限点:");
                    descriptionBuilder.append(httpMethod.permission());
                    descriptionBuilder.append("\n");

                    descriptionBuilder.append("- 权限分组:");
                    descriptionBuilder.append(httpMethod.permissionParentName());
                    descriptionBuilder.append("\n");

                    descriptionBuilder.append("- 权限子分组:");
                    descriptionBuilder.append(httpMethod.permissionName());
                    descriptionBuilder.append("\n");
                }
                if (apiLog != null) {
                    descriptionBuilder.append("\n\n");
                    descriptionBuilder.append(String.join("\n - ", apiLog.value()));
                }
                postOperation.setDescription(descriptionBuilder.toString());
                postOperation.setParameters(new ArrayList<>());
                postOperation.setRequestBody(new RequestBody());
                postOperation.getRequestBody().setContent(new Content());
                MediaType mediaType = new MediaType();
                Schema<?> schema = new Schema<>();
                mediaType.schema(schema);
                schema.setType("object");
                schema.setProperties(new LinkedHashMap<>());
                Type[] genericParameterTypes = method.getGenericParameterTypes();
                Parameter[] parameters = method.getParameters();
                List<String> requiredList = new ArrayList<>();

                boolean anyFile = false;
                // 参数
                if (parameters != null) {
                    for (int i = 0; i < parameters.length; i++) {
                        Parameter parameter = parameters[i];
                        Type genericParameterType = genericParameterTypes[i];
                        Type[] actualTypeArguments = new Type[0];
                        if (genericParameterType instanceof ParameterizedType) {
                            actualTypeArguments = ((ParameterizedType) genericParameterType).getActualTypeArguments();
                        }
                        Schema<?> schemaProperty = new Schema<>();
                        HttpParam httpParam = parameter.getAnnotation(HttpParam.class);
                        if (httpParam == null) {
                            logger.info("[Api] 参数未注解: {}", parameter.getName());
                            break;
                        }
                        // 若非这三种类型，则直接不显示在文档上面
                        if (httpParam.type() != HttpParamType.COMMON && httpParam.type() != HttpParamType.FILE && httpParam.type() != HttpParamType.EXCEL) {
                            continue;
                        }
                        // 封装类型
                        Class<?> parameterType = parameter.getType();
                        packOpenApiType(parameterType, schemaProperty);
                        if (httpParam.type() == HttpParamType.FILE || httpParam.type() == HttpParamType.EXCEL) {
                            // 单独处理下这两个
                            schemaProperty.setType("file");
                        }
                        // 将类名，设置为title
                        schemaProperty.setTitle(parameterType.getSimpleName());
                        BaseEnums[] enumConstants = httpParam.enums().getEnumConstants();
                        if (httpParam.enums() != EmptyEnums.class && enumConstants.length > 0) {
                            Class<? extends BaseEnums> enums = httpParam.enums();
                            schemaProperty.setDescription(httpParam.description() + ":" + this.getEnumsMemo(enums) + "\n\n枚举类:" + httpParam.enums().getTypeName());
                        } else {
                            schemaProperty.setDescription(httpParam.description());
                        }
                        if (parameter.getAnnotation(NotNull.class) != null) {
                            requiredList.add(httpParam.name());
                        }
                        if ("object".equals(schemaProperty.getType())) {
                            Schema<?> entitySchema = this.generateEntitySchema(parameterType, new Stack<>(), actualTypeArguments);
                            schemaProperty.setProperties(entitySchema.getProperties());
                            schemaProperty.setRequired(entitySchema.getRequired());
                        }
                        if ("array".equals(schemaProperty.getType())) {
                            Class<?> clazz = httpParam.arrayClass();
                            Schema<?> arrayItem = generateEntitySchema(clazz, new Stack<>(), actualTypeArguments);
                            schemaProperty.setItems(arrayItem);
                        }
                        if ("file".equals(schemaProperty.getType())) {
                            anyFile = true;
                        }
                        schema.getProperties().put(httpParam.name(), schemaProperty);
                    }
                }
                schema.setRequired(requiredList);
                if (anyFile) {
                    postOperation.getRequestBody().getContent().put(org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE, mediaType);
                } else {
                    postOperation.getRequestBody().getContent().put(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, mediaType);
                }

                // 返回值
                Class<?> returnType = method.getReturnType();
                postOperation.setResponses(new ApiResponses());
                ApiResponse apiResponse = new ApiResponse();
                postOperation.getResponses().put("200", apiResponse);
                apiResponse.setDescription("成功");
                apiResponse.setContent(new Content());
                Content responseContent = apiResponse.getContent();
                MediaType responseMediaType = new MediaType();
                Schema<?> responseSchema;
                if (returnType == Flux.class) {
                    responseContent.put(org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE, responseMediaType);
                    responseSchema = new Schema<>();
                    responseSchema.setType("object");
                    responseSchema.setProperties(new HashMap<>());
                    responseMediaType.setSchema(responseSchema);
                    paths.put("/sse.api/" + gpKey + "/" + methodNameKey, pathItem);
                } else {
                    responseContent.put(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, responseMediaType);
                    // GatewayResponse 对应文档
                    responseSchema = generateEntitySchema(GatewayResponse.class, new Stack<>(), method.getGenericReturnType());
                    // Data对应文档
                    Schema<?> dataSchema = responseSchema.getProperties().get("data");
                    responseMediaType.setSchema(responseSchema);
                    packOpenApiType(returnType, dataSchema);
                    ApiEntity apiEntity = returnType.getAnnotation(ApiEntity.class);
                    if (apiEntity != null) {
                        dataSchema.setDescription(returnType.getSimpleName() + "," + apiEntity.description());
                    } else {
                        dataSchema.setDescription(returnType.getSimpleName());
                    }
                    if ("object".equals(dataSchema.getType())) {
                        Type genericReturnType = method.getGenericReturnType();
                        Schema<?> entitySchema = this.generateEntitySchema(returnType, new Stack<>(), genericReturnType instanceof ParameterizedType
                                ? ((ParameterizedType) genericReturnType).getActualTypeArguments() : new Type[0]);
                        responseSchema.getProperties().put("data", entitySchema);
                    } else if ("array".equals(dataSchema.getType())) {
                        // 如果是数组，则需要进一步递归
                        Type genericType = method.getGenericReturnType();
                        if (genericType instanceof ParameterizedType) {
                            // 如果加了泛型
                            Type[] actualTypeArgument = ((ParameterizedType) genericType).getActualTypeArguments();
                            Type type = actualTypeArgument[0];
                            if (type instanceof Class<?>) {
                                // 直接传入的类型
                                Schema<?> itemSchema = generateEntitySchema((Class<?>) actualTypeArgument[0], new Stack<>(), actualTypeArgument);
                                dataSchema.setItems(itemSchema);
                            }
                        }
                        if (dataSchema.getItems() == null) {
                            // 没加泛型
                            dataSchema.setItems(new Schema<>());
                        }
                    }
                    paths.put("/m.api/" + gpKey + "/" + methodNameKey, pathItem);
                }
                // 添加示例返回值
                if (httpMethod.examples().length > 0) {
                    List list = Arrays.asList(httpMethod.examples());
                    responseSchema.setExamples(list);
                }
            }
        }

        return openAPI;
    }

    /**
     *
     * @param clazz 参数/属性/返回值
     * @param parents
     * @param types 参数/属性/返回值 对应的泛型列表
     * @return
     */
    public Schema<?> generateEntitySchema(Class<?> clazz, Stack<Class<?>> parents, Type ...types) {
        // 入栈
        parents.push(clazz);
        Field[] declaredFields = FieldUtils.getAllFields(clazz);
        Schema<?> schema = new Schema<>();
        String schemaType = getClassSchemaType(clazz);
        schema.setType(schemaType);
        if ("object".equals(schemaType)) {
            Map<String, Schema> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();
            schema.setProperties(properties);
            for (Field field : declaredFields) {
                if (Modifier.isStatic(field.getModifiers()) || !hasGetter(clazz, field.getName())) {
                    continue;
                }
                Schema<?> schemaField = new Schema<>();
                Class<?> fieldType = field.getType();
                packOpenApiType(fieldType, schemaField);
                ApiField apiField = field.getAnnotation(ApiField.class);
                if (apiField != null) {
                    BaseEnums[] enumConstants = apiField.enums().getEnumConstants();
                    if (apiField.enums() != EmptyEnums.class && enumConstants.length > 0) {
                        Class<? extends BaseEnums> enums = apiField.enums();
                        schemaField.setDescription(apiField.description() + ":" + this.getEnumsMemo(enums) + "\n\n枚举类：" + apiField.enums().getTypeName());
                    } else {
                        schemaField.setDescription(apiField.description());
                    }
                } else {
                    schemaField.setDescription("暂无描述");
                }

                // 将类名设置为
                schemaField.setTitle(fieldType.getSimpleName());


                if ("object".equals(schemaField.getType())) {
                    // 存在无限递归问题
                    if (parents.contains(field.getType())) {
                        // 如果存在了，就放一个空对象进去，只放一个类名就行了
                        schemaField.setDescription("请参考父节点类型");
                    } else {
                        Type genericType = field.getGenericType();
                        Schema<?> entitySchema = this.generateEntitySchema(fieldType, parents, genericType instanceof ParameterizedType
                                ? ((ParameterizedType) genericType).getActualTypeArguments() : new Type[0]);
                        schemaField.setProperties(entitySchema.getProperties());
                        schemaField.setRequired(entitySchema.getRequired());
                    }
                }
                if ("array".equals(schemaField.getType())) {
                    // 如果是数组，则需要进一步递归
                    Type genericType = field.getGenericType();
                    if (genericType instanceof ParameterizedType) {
                        // 如果加了泛型
                        Type[] actualTypeArgument = ((ParameterizedType) genericType).getActualTypeArguments();
                        Type type = actualTypeArgument[0];
                        if (type instanceof Class<?>) {
                            // 直接传入的类型
                            Class<?> itemClass = (Class<?>) actualTypeArgument[0];
                            if (parents.contains(itemClass)) {
                                // 如果存在了，就放一个空对象进去，只放一个类名就行了, 防止无限递归
                                schemaField.setDescription("请参考父节点类型");
                            } else {
                                String classSchemaType = getClassSchemaType(itemClass);
                                if ("object".equals(classSchemaType) || "array".equals(classSchemaType)) {
                                    Schema<?> itemSchema = generateEntitySchema(itemClass, parents, actualTypeArgument);
                                    schemaField.setItems(itemSchema);
                                } else {
                                    schemaField.setItems(new Schema<>());
                                    schemaField.getItems().setType(classSchemaType);
                                }
                            }
                        } else if (type instanceof TypeVariable<?>){
                            // 泛型传入,取父节点的泛型，但是应该取第几个呢？先乱写取第一个吧
                            Schema<?> itemSchema = generateEntitySchema((Class<?>) types[0], parents, actualTypeArgument);
                            schemaField.setItems(itemSchema);
                        }
                    } else {
                        // 没加泛型
                        schemaField.setItems(new Schema<>());
                    }

                }
                if (field.getAnnotation(NotNull.class) != null) {
                    required.add(field.getName());
                }
                properties.put(field.getName(), schemaField);
            }
            // setRequired时，会copy一份
            schema.setRequired(required);
        }
        // 出栈
        parents.pop();
        return schema;
    }



    private String getEnumsMemo(Class<? extends BaseEnums> clazz) {
        BaseEnums[] enumConstants = clazz.getEnumConstants();
        StringBuilder sb = new StringBuilder();
        for (BaseEnums e : enumConstants) {
            sb.append(e.getCode());
            sb.append("-");
            sb.append(e.getMsg());
            sb.append(" ");
        }
        return sb.toString();
    }


    private boolean hasGetter(Class<?> clazz, String fieldName) {
        String getterName = "get" + StringUtils.upperFirst(fieldName);
        try {
            clazz.getMethod(getterName);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }


    /**
     * 封装OpenAPI类型
     * @param clazz
     * @param schemaProperty
     */
    private static void packOpenApiType(Class<?> clazz, Schema<?> schemaProperty) {
        schemaProperty.setType(getClassSchemaType(clazz));
    }

    private static String getClassSchemaType(Class<?> clazz) {
        String type = "object";
        if (clazz == String.class || clazz == LocalDate.class
                || clazz == LocalDateTime.class || clazz == Date.class) {
            type = "string";
        } else if (clazz == Short.class || clazz == Integer.class || clazz == Long.class
                || clazz == short.class || clazz == int.class || clazz == long.class) {
            type = "integer";
        } else if (clazz == Float.class || clazz == Double.class || clazz == BigDecimal.class
                || clazz == float.class || clazz == double.class) {
            type = "number";
        } else if (List.class.isAssignableFrom(clazz) || Set.class.isAssignableFrom(clazz)) {
            type = "array";
        } else if (clazz == Boolean.class) {
            type = "boolean";
        } else if (clazz == byte[].class || clazz == Byte[].class) {
            // 字节集视为
            type = "file";
        }
        return type;
    }

}

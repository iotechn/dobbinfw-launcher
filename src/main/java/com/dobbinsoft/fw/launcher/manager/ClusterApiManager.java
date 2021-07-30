package com.dobbinsoft.fw.launcher.manager;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dobbinsoft.fw.core.annotation.HttpMethod;
import com.dobbinsoft.fw.core.annotation.HttpOpenApi;
import com.dobbinsoft.fw.core.annotation.HttpParam;
import com.dobbinsoft.fw.core.annotation.doc.ApiEntity;
import com.dobbinsoft.fw.core.annotation.doc.ApiField;
import com.dobbinsoft.fw.core.annotation.param.NotNull;
import com.dobbinsoft.fw.core.enums.BaseEnums;
import com.dobbinsoft.fw.core.enums.EmptyEnums;
import com.dobbinsoft.fw.core.exception.ServiceException;
import com.dobbinsoft.fw.launcher.exception.LauncherExceptionDefinition;
import com.dobbinsoft.fw.launcher.exception.LauncherServiceException;
import com.dobbinsoft.fw.launcher.inter.AfterRegisterApiComplete;
import com.dobbinsoft.fw.support.model.PermissionPoint;
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
import org.springframework.util.StringUtils;

import java.lang.reflect.*;
import java.util.*;

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

    private Map<String, Map<String, Method>> methodCacheMap = new HashMap<>();

    private Map<String, String> groupDescCacheMap = new HashMap<>();

    private ApplicationContext applicationContext;

    private List<PermissionPoint> permDTOs = new LinkedList<>();

    private Set<Class> interfaceClassList = new TreeSet<>(Comparator.comparing(Class::getName));

    private Map<String, String> permissionRouteMap = new HashMap<>();

    @Autowired(required = false)
    private AfterRegisterApiComplete afterRegisterApiComplete;

    @Override
    public void afterPropertiesSet() throws Exception {
        List<Class> classList = new LinkedList<>();
        String[] beanArray = applicationContext.getBeanNamesForAnnotation(Service.class);
        for (String service : beanArray) {
            Object bean = applicationContext.getBean(service);
            String className = bean.toString().split("@")[0];
            Class<?> serviceClazz = Class.forName(className);
            Class<?>[] interfaces = serviceClazz.getInterfaces();
            if (interfaces != null && interfaces.length > 0) {
                for (Class clazz : interfaces) {
                    if (clazz.getAnnotation(HttpOpenApi.class) != null) {
                        classList.add(clazz);
                    }
                }
            }
        }
        for (Class clazz : classList) {
            this.registerService(clazz);
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
                    if (!StringUtils.isEmpty(permission)) {
                        //若此接口需要权限
                        if (StringUtils.isEmpty(httpMethod.permissionParentName()) || StringUtils.isEmpty(httpMethod.permissionName())) {
                            throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_API_REGISTER_FAILED);
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
                        throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_API_REGISTER_FAILED);
                    }
                    tempMap.put(key, method);
                    logger.info("[注册OpenApi] " + group + "." + method.getName());
                } else {
                    logger.info("[注册OpenApi 失败] 没有注解." + method.getName());
                }

            }
        } else {
            throw new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_API_REGISTER_FAILED);
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
    public Method getMethod(String app, String group, String name) {
        Map<String, Method> tempMap = methodCacheMap.get(group);
        if (tempMap != null) {
            return tempMap.get(name);
        }
        return null;
    }

    @Override
    public Object getServiceBean(Method method) {
        Object serviceBean = applicationContext.getBean(method.getDeclaringClass());
        return serviceBean;
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

    /**
     * 获取文档模型的方法
     *
     * @return
     */
    private ApiDocumentModel apiDocumentModelCache = null;

    public ApiDocumentModel generateDocumentModel() {
        if (apiDocumentModelCache != null) {
            return apiDocumentModelCache;
        }
        Set<String> gpKeys = methodCacheMap.keySet();
        ApiDocumentModel apiDocumentModel = new ApiDocumentModel();
        List<ApiDocumentModel.Group> groups = new LinkedList<>();
        apiDocumentModel.setGroups(groups);
        for (String gpKey : gpKeys) {
            ApiDocumentModel.Group group = new ApiDocumentModel.Group();
            groups.add(group);
            group.setName(gpKey);
            group.setDescription(groupDescCacheMap.getOrDefault(gpKey, ""));
            List<ApiDocumentModel.Method> docMethods = new LinkedList<>();
            group.setMethods(docMethods);
            Map<String, Method> methodMap = methodCacheMap.get(gpKey);
            Set<String> methodNameKeys = methodMap.keySet();
            for (String methodNameKey : methodNameKeys) {
                Method method = methodMap.get(methodNameKey);
                //获取参数
                List<ApiDocumentModel.Parameter> docParameters = new LinkedList<>();
                Set<ApiDocumentModel.Entity> docEntities = new HashSet<>();
                Parameter[] parameters = method.getParameters();
                if (parameters != null && parameters.length > 0) {
                    for (Parameter parameter : parameters) {
                        HttpParam httpParam = parameter.getAnnotation(HttpParam.class);
                        ApiDocumentModel.Parameter docParameter = new ApiDocumentModel.Parameter();
                        if (docParameter == null) {
                            logger.info("[Api] 参数未注解:" + method.getName());
                        }
                        if (httpParam == null) {
                            logger.info("[Api] 参数未注解");
                        }
                        docParameter.setName(httpParam.name());
                        docParameter.setDescription(httpParam.description());
                        String typeName = parameter.getType().getTypeName();
                        if (typeName.startsWith("[L")) {
                            typeName = typeName.substring(2) + "[]";
                        }
                        docParameter.setParamType(typeName);
                        docParameter.setType(httpParam.type());
                        docParameter.setRequired(parameter.getAnnotation(NotNull.class) != null);
                        docParameters.add(docParameter);
                        Set<ApiDocumentModel.Entity> entities = this.generateEntityModel(parameter.getType());
                        docEntities.addAll(entities);
                    }
                }
                ApiDocumentModel.Method docMethod = new ApiDocumentModel.Method();
                docMethod.setParameters(docParameters);
                HttpMethod httpMethod = method.getAnnotation(HttpMethod.class);
                if (httpMethod != null) {
                    docMethod.setDescription(httpMethod.description());
                    docMethod.setName(method.getName());
                    Type returnType = method.getGenericReturnType();
                    String retType = returnType.toString();
                    if (retType.startsWith("interface")) {
                        if (retType.startsWith("interface [L")) {
                            retType = retType.substring(12);
                        } else {
                            retType = retType.substring(10);
                        }
                    } else if (retType.startsWith("class")) {
                        if (retType.startsWith("class [L")) {
                            retType = retType.substring(8);
                        } else {
                            retType = retType.substring(6);
                        }
                    }
                    docMethod.setRetType(retType);
                    // 生成返回值文档
                    Class returnClass = null;
                    if (returnType instanceof Class) {
                        returnClass = (Class) returnType;
                    } else if (returnType instanceof ParameterizedType) {
                        ParameterizedType parameterizedType = (ParameterizedType) returnType;
                        Type[] types = parameterizedType.getActualTypeArguments();
                        if (types.length > 1) {
                            returnClass = (Class) (parameterizedType.getRawType());
                        } else {
                            Type type = types[0];
                            if (type instanceof ParameterizedType) {
                                returnClass = (Class) ((ParameterizedType) type).getRawType();
                            } else {
                                returnClass = (Class) type;
                            }
                        }
                    }
                    ApiEntity apiEntity = (ApiEntity) returnClass.getAnnotation(ApiEntity.class);
                    if (apiEntity != null) {
                        //若返回值类型为复杂类型
                        List<ApiDocumentModel.Field> fieldList = new ArrayList<>();
                        if (returnClass != null) {
                            Field[] declaredFields = returnClass.getDeclaredFields();
                            for (Field field : declaredFields) {
                                ApiDocumentModel.Field docField = new ApiDocumentModel.Field();
                                ApiField apiField = field.getAnnotation(ApiField.class);
                                if (apiField != null) {
                                    BaseEnums[] enumConstants = apiField.enums().getEnumConstants();
                                    if (apiField.enums() != EmptyEnums.class && enumConstants.length > 0) {
                                        Class<? extends BaseEnums> enums = apiField.enums();
                                        docField.setDescription(apiField.description() + ":" + this.getEnumsMemo(enums));
                                        docField.setMap(enumConstants[0].getMap().replace("\n", "\\n").replace("'", "\\'"));
                                        docField.setFilter(enumConstants[0].getFilter().replace("\n", "\\n").replace("'", "\\'"));
                                        docField.setEnums(enumConstants);
                                    } else {
                                        docField.setDescription(apiField.description());
                                    }
                                } else {
                                    docField.setDescription("暂无描述");
                                }
                                docField.setName(field.getName());

                                Class type;
                                if (Collection.class.isAssignableFrom(field.getType())
                                        || IPage.class.isAssignableFrom(field.getType())) {
                                    // List 派生
                                    Type actualTypeArgument = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                                    if (actualTypeArgument instanceof ParameterizedType) {
                                        type = (Class) ((ParameterizedType) actualTypeArgument).getRawType();
                                    } else {
                                        type = (Class) actualTypeArgument;
                                    }
                                    docField.setType(field.getGenericType().toString().replace("<", "&lt;").replace(">", "&gt;"));
                                } else {
                                    type = field.getType();
                                    docField.setType(field.getType().getTypeName());
                                }
                                fieldList.add(docField);
                                // 生成实体文档
                                docEntities.addAll(generateEntityModel(type));
                            }
                            docMethod.setRetObj(fieldList);
                        }
                    }
                    if (returnClass == null && returnType != null) {
                        ApiDocumentModel.Field docField = new ApiDocumentModel.Field();
                        docField.setDescription("未知类型");
                        docField.setType(returnType.toString());
                        docField.setName("unanme");
                        docMethod.setRetObj(Arrays.asList(docField));
                    }
                    docMethod.setEntityList(new ArrayList<>(docEntities));
                    docMethods.add(docMethod);

                } else {
                    logger.info("生成文档失败:" + method.getName());
                }
            }
        }
        apiDocumentModelCache = apiDocumentModel;
        return apiDocumentModel;
    }

    public ApiDocumentModel.Group generateGroupModel(String group) {
        ApiDocumentModel apiDocumentModel = generateDocumentModel();
        List<ApiDocumentModel.Group> groups = apiDocumentModel.getGroups();
        for (ApiDocumentModel.Group gp : groups) {
            if (gp.getName().equals(group)) {
                return gp;
            }
        }
        return null;
    }

    public ApiDocumentModel.Method generateMethodModel(String gp, String mt) {
        ApiDocumentModel apiDocumentModel = generateDocumentModel();
        List<ApiDocumentModel.Group> groups = apiDocumentModel.getGroups();
        for (ApiDocumentModel.Group group : groups) {
            if (group.getName().equals(gp)) {
                List<ApiDocumentModel.Method> methods = group.getMethods();
                for (ApiDocumentModel.Method method : methods) {
                    if (method.getName().equals(mt)) {
                        return method;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 生成实例模型
     *
     * @param clazz
     * @return
     */
    public Set<ApiDocumentModel.Entity> generateEntityModel(Class clazz) {
        HashSet<ApiDocumentModel.Entity> entities = new HashSet<>();
        return generateEntityModel(clazz, entities);
    }

    private Set<ApiDocumentModel.Entity> generateEntityModel(Class clazz, HashSet<ApiDocumentModel.Entity> entities) {
        ApiEntity apiEntity = (ApiEntity) clazz.getAnnotation(ApiEntity.class);
        if (apiEntity != null) {
            ApiDocumentModel.Entity entity = new ApiDocumentModel.Entity();
            entity.setDescription(apiEntity.description());
            entity.setType(clazz.getTypeName());
            if (!entities.contains(entity)) {
                entities.add(entity);
                Field[] declaredFields = clazz.getDeclaredFields();
                List<ApiDocumentModel.Field> fieldList = new LinkedList<>();
                for (Field field : declaredFields) {
                    ApiDocumentModel.Field docField = new ApiDocumentModel.Field();
                    ApiField apiField = field.getAnnotation(ApiField.class);
                    if (apiField != null) {
                        BaseEnums[] enumConstants = apiField.enums().getEnumConstants();
                        if (apiField.enums() != EmptyEnums.class && enumConstants.length > 0) {
                            Class<? extends BaseEnums> enums = apiField.enums();
                            docField.setDescription(apiField.description() + ":" + this.getEnumsMemo(enums));
                            docField.setMap(enumConstants[0].getMap().replace("\n", "\\n").replace("'", "\\'"));
                            docField.setFilter(enumConstants[0].getFilter().replace("\n", "\\n").replace("'", "\\'"));
                            docField.setEnums(enumConstants);
                        } else {
                            docField.setDescription(apiField.description());
                        }
                    } else {
                        docField.setDescription("暂无描述");
                    }
                    docField.setName(field.getName());
                    Class type;
                    if (Collection.class.isAssignableFrom(field.getType())
                            || IPage.class.isAssignableFrom(field.getType())) {
                        // List 派生
                        Type actualTypeArgument = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                        if (actualTypeArgument instanceof ParameterizedType) {
                            type = (Class) ((ParameterizedType) actualTypeArgument).getRawType();
                        } else {
                            type = (Class) actualTypeArgument;
                        }
                        docField.setType(field.getGenericType().toString().replace("<", "&lt;").replace(">", "&gt;"));
                    } else {
                        type = field.getType();
                        docField.setType(field.getType().getTypeName());
                    }
                    fieldList.add(docField);
                    // 递归生成子文档 无限树结构问题？
                    entities.addAll(generateEntityModel(type, entities));
                }
                entity.setFields(fieldList);
            }
        }
        return entities;
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

    public List<ApiDocumentModel.Method> methods(String gp) {
        for (ApiDocumentModel.Group group : generateDocumentModel().getGroups()) {
            if (group.getName().equals(gp)) {
                return group.getMethods();
            }
        }
        return null;
    }

}

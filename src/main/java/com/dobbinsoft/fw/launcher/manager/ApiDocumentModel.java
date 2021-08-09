package com.dobbinsoft.fw.launcher.manager;


import com.dobbinsoft.fw.core.annotation.HttpParamType;
import com.dobbinsoft.fw.core.enums.BaseEnums;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * Description:
 * User: rize
 * Date: 2018-09-13
 * Time: 上午9:35
 */
@Data
public class ApiDocumentModel {
    private List<Group> groups;
    private boolean openPlatform;

    @Data
    public static class Group {
        /**
         * 组名
         */
        private String name;

        /**
         * 描述
         */
        private String description;
        /**
         * 方法列表
         */
        private List<Method> methods;

    }

    @Data
    public static class Method {
        private String name;
        private String description;
        private String retType;
        /**
         * 是否是开放平台接口
         */
        private Boolean openPlatform;
        private List<Field> retObj;
        private List<Parameter> parameters;
        private List<Entity> entityList;
    }

    @Data
    public static class Parameter {
        private String name;
        private String description;
        /**
         * 参数class类型
         */
        private String paramType;
        /**
         * 参数枚举
         */
        private HttpParamType type;
        private Boolean required;
        /**
         * 是否是JSON对象
         */
        private Boolean json;

    }

    /**
     * 请求实体
     */
    @Data
    @EqualsAndHashCode(exclude = "fields")
    public static class Entity {
        private String type;
        private String description;
        private List<Field> fields;
    }

    @Data
    public static class Field {
        private String name;
        private String type;
        private String description;
        private String map;
        private String filter;
        private BaseEnums[] enums;
    }

}

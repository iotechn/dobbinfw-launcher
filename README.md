![](https://doc-1324075299.cos.ap-guangzhou.myqcloud.com/dobbinfw/banner.jpg)

[![Release Version](https://img.shields.io/badge/release-2.0.2-brightgreen.svg)](https://gitee.com/iotechn/dobbinfw-launcher) [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://gitee.com/iotechn/unimall/pulls)

#### 一、项目背景 

> 为了快速落地项目、快速搭建脚手架，dobbinsoft开发一套基于SpringBoot3 MyBatis的框架，并手搓了如参数校验、文档生成、限流、鉴权、RPC等等常用功能。launcher包对外提供Web服务，进行路由&接口鉴权。
> Dobbin Framework旨在以最少的依赖，最轻量的infra资源开发出稳定健壮的Web应用。



#### 二、快速开始

引入maven坐标到工程pom.xml文件中。

```xml
<dependency>
    <groupId>com.dobbinsoft</groupId>
    <artifactId>fw-launcher</artifactId>
    <version>2.x.x</version>
</dependency>
```

版本号可在maven库中查询：https://central.sonatype.com/artifact/com.dobbinsoft/fw-launcher/versions

#### 三、功能支持

[3.1. Api鉴权与路由](./doc/31.auth.md)

[3.2. 文档生成](32.doc.md)

[3.3. 文件上传](33.upload.md)

3.4. 健康检测

GET /health/ping 即可，用于负载均衡健康检测、K8S探针等常见。


#### 四、模板方法与观测点

[4.1. 自定义服务调用方式(CustomInvoker)](./41.doc.invoker.md)

[4.2. Before Http](./doc/before-http.md)

[4.3. After Http](43.after-http.md)

#### 五、服务
![](https://doc-1324075299.cos.ap-guangzhou.myqcloud.com/dobbinfw/dobbinfw-digest.jpg)

#### 五、贡献 & 社区
若Launcher包不能满足您的业务需求，您可以直接在仓库中发布Pull Request。本项目欢迎所有开发者一起维护，并永久开源。


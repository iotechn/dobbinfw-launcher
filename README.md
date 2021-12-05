## Dobbin Framework Launcher Logo

#### 一、项目背景 

> 为了快速落地项目、快速搭建脚手架，dobbinsoft开发一套基于SpringBoot MyBatis的框架，并手搓了如参数校验、文档生成、限流、鉴权等等常用功能。launcher包对外提供Web服务，进行路由&接口鉴权。


#### 二、快速开始

##### 2.1. 下载代码

您可以在国内开源社区Gitee下载（推荐）：https://gitee.com/iotechn/dobbinfw-launcher

您可以在国际开源社区Github下载：https://github.com/iotechn/dobbinfw-launcher

##### 2.2. maven引入

请确定您已经将 JAVA_HOME 配置，并将mvn命令配置到PATH中，若出现找不到命令，或找不到JAVA_HOME，[请参考此文档](https://blog.csdn.net/weixin_44548718/article/details/108635409)

在项目根目录，打开命令行。并执行 ：

```shell
mvn install -Dmaven.test.skip=true
```

引入maven坐标到工程pom.xml文件中。

```xml
<groupId>com.dobbinsoft</groupId>
<artifactId>fw-launcher</artifactId>
<version>1.0-SNAPSHOT</version>
```

##### PS. 请注意

请确认已经引入fw-core，[请参照项目](../dobbinfw-core) ，请确认已经安装fw-support，[请参照项目]((../dobbinfw-support))

#### 三、功能支持

[3.1. Api鉴权与路由](./doc/31.auth.md)

[3.2. 文档生成](32.doc.md)

[3.3. 文件上传](33.upload.md)

3.4. 健康检测

​       GET /health/ping 即可，用于负载均衡健康检测、K8S探针等常见。

3.5. UEditor富文本编辑器支持

​       将前端的serverUrl配置为 "serverUrl":"/ueditor/cos" 即可

#### 四、模板方法与观测点

[4.1. 自定义服务调用方式(CustomInvoker)](./41.doc.invoker.md)

[4.2. Before Http](./doc/before-http.md)

[4.3. After Http](43.after-http.md)

.. com.dobbinsoft.fw.launcher.inter; 详细请查看这个包


#### 五、贡献 & 社区
若Launcher包不能满足您的业务需求，您可以直接在仓库中发布Pull Request。本项目欢迎所有开发者一起维护，并永久开源。


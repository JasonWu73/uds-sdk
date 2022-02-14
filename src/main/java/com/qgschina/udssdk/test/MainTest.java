package com.qgschina.udssdk.test;

import com.qgschina.udssdk.client.Client;
import com.qgschina.udssdk.client.constant.UdsCode;
import com.qgschina.udssdk.client.model.UdsResult;
import com.qgschina.udssdk.common.constant.ServiceType;
import com.qgschina.udssdk.common.model.NamespaceResultData;
import com.qgschina.udssdk.server.Server;
import com.qgschina.udssdk.test.service.TestService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 用于对 UDS 服务的一个可用性测试类, 包含 Server 端和 Client 端
 * <p>
 * 注意: 本 SDK 仅支持在 Linux 下运行
 */
public class MainTest {

  /**
   * 假定一个用于"信号订阅"的信号名
   */
  private static final String SUB_SIGNAL = "sub_shutdown";

  /**
   * 用于让主线程有机会等待其他线程执行完后再执行
   */
  private static final CountDownLatch latch = new CountDownLatch(1);

  /**
   * Main 方法
   *
   * @param args 命令行参数,
   *             参数 1: {@code [server|client]},
   *             参数 2: {@code namespace}
   * @throws InterruptedException 可忽略
   */
  public static void main(String[] args) throws InterruptedException {
    if (args == null || args.length != 2) {
      System.out.println("运行方式 :)\n"
          + "java -jar uds-sdk-<version>.jar "
          + "<server/client> <namespace>");
      return;
    }

    String runType = args[0];
    String namespace = args[1];

    if (Objects.equals("server", runType)) {
      // SDK Server 端使用方式
      useServer(namespace);
      return;
    }

    if (Objects.equals("client", runType)) {
      // SDK Client 端使用方式
      useClient(namespace);
      return;
    }

    System.out.println("参数错误 :(");
  }

  /**
   * SDK Server 端使用方式
   *
   * @param namespace 用于开启服务的命名空间
   * @throws InterruptedException 可忽略
   */
  private static void useServer(String namespace) throws InterruptedException {
    // 初始化 Server 配置, 参数分别为:
    // 1. 服务类型
    // 2. 服务子命名空间
    // 3. 允许数据传输的最大字节数, 单位: MB
    Server.init(ServiceType.CUSTOM, namespace, 10);

    // 获取 Server 单例对象
    Server server = Server.getInstance();

    // 注册服务对象 (方法调用及信号触发)
    server.registerService(new TestService());

    // 注册信号订阅
    server.registerSubSignal(SUB_SIGNAL);

    System.out.println("模拟仅运行 10 秒的 Servlet 容器...");
    boolean countDown0 = latch.await(10, TimeUnit.SECONDS);
    System.out.printf("10 秒已到 (%s), 模拟关闭 Servlet 容器\n", !countDown0);

    System.out.println("消息推送: 服务即将关闭");
    // 消息推送
    server.send(SUB_SIGNAL, "服务即将关闭");

    // 关闭 Server
    // 注意: 通常 Server 都不是通过自身程序关闭的, 故执行本方法并不会关闭 JVM
    server.stop();

    // 模拟正常关闭 JVM
    System.exit(0);
  }

  /**
   * SDK Client 端使用方式
   *
   * @param namespace 用于连接服务的命名空间
   * @throws InterruptedException 可忽略
   */
  private static void useClient(String namespace) throws InterruptedException {
    // 创建新的 Client, 参数分别为:
    // 1. 服务类型
    // 2. 服务子命名空间
    // 3. 等待 Server 端返回的超时时间, 单位: 秒 (对订阅不生效)
    // 4. 允许数据传输的最大字节数, 单位: MB
    Client client = new Client(ServiceType.CUSTOM, namespace, 10, 10);

    // 信号订阅 (长连接)
    System.out.println("信号订阅 --> ");
    UdsResult<Client.UdsConnection> subResult = client
        .subSignal(obj -> {
          System.out.println("\n>>>>>>>>>>>>>>>>>>>>>>>>");
          System.out.println("订阅结果: " + obj);
          System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<\n");
        }, SUB_SIGNAL);
    checkUdsCode(subResult);

    // 获取当前命名空间下的所有可被方法调用的方法
    System.out.println("所有方法 --> ");
    UdsResult<NamespaceResultData> allMethods = client.getAllMethods();
    checkUdsCode(allMethods);

    // 获取当前命名空间下的所有可被触发或订阅的信号
    System.out.println("所有信号 (触发和订阅) --> ");
    UdsResult<NamespaceResultData> allSignals = client.getAllSignals();
    checkUdsCode(allSignals);

    // 获取当前命名空间下的所有可被调用的方法, 及可被触发或订阅的信号
    System.out.println("所有方法和信号 --> ");
    UdsResult<NamespaceResultData> allMethodsAndSignals =
        client.getAllMethodsAndSignals();
    checkUdsCode(allMethodsAndSignals);

    // 方法调用
    System.out.println("方法调用 --> ");
    UdsResult<Object> resultEcho =
        client.callMethod("echo",
            "Java 微服务 SDK v0.0.1".getBytes(StandardCharsets.UTF_8),
            new ArrayList<String>() {{
              add("C++");
              add("Java");
              add("Python");
            }});
    checkUdsCode(resultEcho);

    // 信号触发
    System.out.println("信号触发 --> ");
    UdsResult<Void> resultSignal =
        client.triggerSignal("trigger",
            new HashMap<String, Object>() {{
              put("name", "Java 开发包");
              put("version", 1);
            }}, 99.9999999);
    checkUdsCode(resultSignal);

    System.out.println("模拟一个仅运行 12 秒的 Client 服务");
    boolean reached0 = latch.await(12, TimeUnit.SECONDS);
    System.out.printf("12 秒已到 (%s), 模拟关闭 Client 服务\n", !reached0);

    // 关闭与 Server 端建立的信号订阅长连接
    subResult.getData().disconnectSub();
  }

  /**
   * 检查 UDS 调用返回结果的响应码
   *
   * @param result UDS 返回结果
   */
  private static void checkUdsCode(UdsResult<?> result) {
    if (result.getCode() == UdsCode.SUCCESS) {
      System.out.println("正常返回: " + result);
      System.out.println("========================\n");
      return;
    }
    if (result.getCode() == UdsCode.NOT_CONNECTED) {
      throw new RuntimeException("未连接: " + result);
    }
    if (result.getCode() == UdsCode.OVER_TIME) {
      throw new RuntimeException("超时: " + result);
    }
    if (result.getCode() == UdsCode.METHOD_CALL_ERROR) {
      throw new RuntimeException("函数调用失败: " + result);
    }
    if (result.getCode() == UdsCode.SIGNAL_SUB_ERROR) {
      throw new RuntimeException("信号订阅失败: " + result);
    }
  }
}

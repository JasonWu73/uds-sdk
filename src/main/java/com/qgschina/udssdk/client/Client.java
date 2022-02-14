package com.qgschina.udssdk.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.qgschina.udssdk.client.constant.UdsCode;
import com.qgschina.udssdk.client.model.UdsResult;
import com.qgschina.udssdk.common.constant.InnerErrorCode;
import com.qgschina.udssdk.common.constant.InnerReqType;
import com.qgschina.udssdk.common.constant.ServiceType;
import com.qgschina.udssdk.common.exception.DataProcessException;
import com.qgschina.udssdk.common.exception.ParamException;
import com.qgschina.udssdk.common.exception.UdsSdkException;
import com.qgschina.udssdk.common.model.InnerReqData;
import com.qgschina.udssdk.common.model.InnerRespData;
import com.qgschina.udssdk.common.model.NamespaceResultData;
import com.qgschina.udssdk.common.util.DataTypeUtils;
import com.qgschina.udssdk.common.util.JsonUtils;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.codec.json.JsonObjectDecoder;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.CharsetUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.DisposableChannel;
import reactor.netty.tcp.TcpClient;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 用于连接 Server 执行接口调用
 */
@Slf4j
public class Client {

  /**
   * 最终连接服务时使用的 Socket 文件所在路径
   */
  private final String domainSocketAddr;

  /**
   * JSON 支持的最大字节数, 单位 MB
   */
  private final int maxBytesMb;

  /**
   * 等待 Server 端返回数据的超时时间
   */
  private final int timeout;

  /**
   * 创建一个新的 Client 实例
   *
   * @param type       服务类型
   * @param namespace  服务子命名空间
   * @param timeout    等待 Server 端返回的超时时间, 单位: 秒 (对订阅不生效)
   * @param maxBytesMb 允许数据传输的最大字节数, 单位: MB
   */
  public Client(ServiceType type, String namespace, int timeout,
      int maxBytesMb) {
    domainSocketAddr = type.value() + "." + namespace;
    this.maxBytesMb = maxBytesMb;
    this.timeout = timeout;
  }

  /**
   * 信号订阅 (长连接)
   * <p>
   * 当 Server 对指定信号进行消息推送时, 就会执行 {@code consumer}
   *
   * @param consumer 当 Server 端有返回结果时的消费者
   * @param signal   需要进行"信号订阅"的信号名
   * @param args     传递给 Server 的参数, 可能在首次建立订阅时需要
   */
  public UdsResult<UdsConnection> subSignal(
      Consumer<UdsResult<Object>> consumer,
      String signal, Object... args) {
    TcpClient client = getClient();

    InnerReqData reqData = genReqData(InnerReqType.SIGNAL_SUB, signal, args);

    String jsonReq;
    try {
      jsonReq = JsonUtils.toJson(reqData);
    } catch (JsonProcessingException e) {
      throw new DataProcessException("SDK 序列化失败", e);
    }

    Connection conn;

    try {
      conn = client
          .handle((in, out) -> out
              .sendString(Mono.just(jsonReq))
              .then(in.receive()
                  .asString(CharsetUtil.UTF_8)
                  .log()
                  .doOnNext(jsonResp -> handleSubSignal(consumer, jsonResp))
                  .then()))
          .connectNow();
    } catch (Exception e) {
      return handleConnectException(e);
    }

    return genResult(UdsCode.SUCCESS, "服务连接成功", new UdsConnection(conn));
  }

  /**
   * 获取当前命名空间下的所有可被方法调用的方法
   *
   * @return 所有可被方法调用的方法
   */
  public UdsResult<NamespaceResultData> getAllMethods() {
    return callMethod(InnerReqType.GET_ALL_METHODS, null, null);
  }

  /**
   * 获取当前命名空间下的所有可被触发或订阅的信号
   *
   * @return 所有可被触发或订阅的信号
   */
  public UdsResult<NamespaceResultData> getAllSignals() {
    return callMethod(InnerReqType.GET_ALL_SIGNALS, null, null);
  }

  /**
   * 获取当前命名空间下的所有可被调用的方法, 及可被触发或订阅的信号
   *
   * @return 所有可被调用的方法, 及可被触发或订阅的信号
   */
  public UdsResult<NamespaceResultData> getAllMethodsAndSignals() {
    return callMethod(InnerReqType.GET_ALL_METHODS_SIGNALS, null, null);
  }

  /**
   * 方法调用
   * <p>
   * 同步调用某个方法
   *
   * @param method 方法名
   * @param args   可变长方法参数
   * @return 执行指定方法后的返回结果
   */
  public UdsResult<Object> callMethod(String method, Object... args) {
    return callMethod(InnerReqType.CALL_METHOD, method, args);
  }

  /**
   * 信号触发
   * <p>
   * 异步调用某个方法, 且不存在返回值
   *
   * @param signal 信号名
   * @param args   可变长信号参数
   * @return 信号是否触发成功
   */
  public UdsResult<Void> triggerSignal(String signal, Object... args) {
    return callMethod(InnerReqType.SIGNAL, signal, args);
  }

  /**
   * 获取配置后的 {@link TcpClient}
   *
   * @return 配置后的 {@link TcpClient}
   */
  private TcpClient getClient() {
    return TcpClient.create()
        // Unix Domain Sockets (UDS)
        .remoteAddress(() -> new DomainSocketAddress(domainSocketAddr))
        // 开启 Wire Logger
        .wiretap(true)
        .doOnConnected(conn -> conn
            // 各语言间数据都是通过 JSON 字符串传递
            // 默认最大支持 1 MB 字节数
            .addHandler(new JsonObjectDecoder(1024 * 1024 * maxBytesMb)));
  }

  /**
   * 执行方法调用
   *
   * @param type   请求类型
   * @param signal 信号名或方法名
   * @param args   请求参数 (方法参数)
   * @param <T>    具体的返回数据结果类型
   * @return UDS 响应结果
   */
  private <T> UdsResult<T> callMethod(InnerReqType type, String signal,
      Object[] args) {
    TcpClient client = getClient()
        .doOnConnected(conn -> conn
            // 等待 Server 端返回的超时时间
            .addHandler(new ReadTimeoutHandler(timeout, TimeUnit.SECONDS)));

    InnerReqData reqData = genReqData(type, signal, args);

    String jsonReq;
    try {
      jsonReq = JsonUtils.toJson(reqData);
    } catch (JsonProcessingException e) {
      throw new DataProcessException("SDK 序列化失败", e);
    }

    UdsResult<T> result = new UdsResult<>();
    result.setCode(UdsCode.METHOD_CALL_ERROR);
    result.setMessage("发送的数据量过大, 详情请查看 Server 日志");

    Connection conn;
    try {
      conn = client
          .handle((in, out) -> out
              .sendString(Mono.just(jsonReq))
              .then(in
                  .receive()
                  .asString(CharsetUtil.UTF_8)
                  .log() // 记录 Log
                  .doOnNext(jsonResp -> handleResp(result, out, jsonResp))
                  .then()
                  .doOnError(thr -> handleError(result, thr))))
          .connectNow();
    } catch (Exception e) {
      return handleConnectException(e);
    }

    conn.onDispose().block();

    return result;
  }

  private <T> UdsResult<T> handleConnectException(Exception e) {
    if (e.getCause() instanceof FileNotFoundException) {
      return genResult(UdsCode.NOT_CONNECTED, "无法连接 Server", null);
    }

    throw new UdsSdkException("连接 Server 失败", e);
  }

  /**
   * Client 内部异常处理
   *
   * @param result 最终返回给用户的结果
   * @param thr    Client 中捕获的异常
   * @param <T>    最终返回给用户的结果的返回值类型
   */
  private <T> void handleError(UdsResult<T> result, Throwable thr) {
    if (thr instanceof ReadTimeoutException) {
      result.setCode(UdsCode.OVER_TIME);
      result.setMessage("连接超时");
    } else {
      result.setCode(UdsCode.METHOD_CALL_ERROR);
      result.setMessage("Client Channel 错误: " + thr.getMessage());
    }
  }

  /**
   * 处理 Server 数据推送
   *
   * @param consumer 消费者
   * @param jsonResp Server 响应 JSON 数据
   */
  private void handleSubSignal(Consumer<UdsResult<Object>> consumer,
      String jsonResp) {
    try {
      TypeReference<InnerRespData<Object>> ref =
          new TypeReference<InnerRespData<Object>>() {
          };
      InnerRespData<Object> respData =
          JsonUtils.parseJson(jsonResp, ref);

      UdsCode udsCode = respData.getCode() ==
          InnerErrorCode.ERROR.value() ?
          UdsCode.SIGNAL_SUB_ERROR : UdsCode.SUCCESS;

      consumer.accept(genResult(udsCode, respData.getMsg(),
          respData.getData()));
    } catch (Exception e) {
      throw new DataProcessException("Client 数据处理异常: "
          + e.getMessage(), e);
    }
  }

  /**
   * 处理 Server 响应数据
   *
   * @param result   最终返回给用户的结果
   * @param out      Netty 出栈
   * @param jsonResp Server 响应 JSON 数据
   * @param <T>      最终返回给用户的结果的返回值类型
   */
  private <T> void handleResp(UdsResult<T> result,
      reactor.netty.NettyOutbound out, String jsonResp) {
    try {
      TypeReference<InnerRespData<Object>> ref =
          new TypeReference<InnerRespData<Object>>() {
          };
      InnerRespData<Object> respData =
          JsonUtils.parseJson(jsonResp, ref);

      result.setMessage(respData.getMsg());
      //noinspection unchecked
      result.setData((T) respData.getData());

      result.setCode(respData.getCode() ==
          InnerErrorCode.ERROR.value() ?
          UdsCode.METHOD_CALL_ERROR : UdsCode.SUCCESS);
    } catch (Exception e) {
      log.error("Client 数据处理异常", e);
      result.setCode(UdsCode.METHOD_CALL_ERROR);
      result.setMessage("Client 数据处理异常: " + e.getMessage());
    }

    out.withConnection(DisposableChannel::dispose);
  }

  /**
   * 构造请求数据
   *
   * @param type   SDK 内部请求类型
   * @param signal 信号名或方法名
   * @param args   传递给 Server 的参数
   * @return 请求数据
   */
  private InnerReqData genReqData(InnerReqType type, String signal,
      Object[] args) {
    InnerReqData reqData = new InnerReqData();
    reqData.setType(type.value());

    if (type == InnerReqType.CALL_METHOD) {
      reqData.setMethod(signal);
    } else {
      reqData.setSignal(signal);
    }

    if (args != null && args.length > 0) {
      reqData.setData(Arrays.asList(args));

      List<String> paramTypes = new ArrayList<>();
      for (Object arg : args) {
        Optional<String> typeName = DataTypeUtils.getGeneralParamTypeName(arg);
        if (!typeName.isPresent()) {
          throw new ParamException("参数类型不支持: "
              + arg.getClass().getSimpleName());
        }

        paramTypes.add(typeName.get());
      }

      reqData.setParameterTypes(paramTypes);
    }

    return reqData;
  }

  /**
   * 构造 UDS 响应结果
   *
   * @param code    UDS 响应码
   * @param message 提示消息
   * @param data    具体数据结果
   * @param <T>     具体结果类型
   * @return UDS 响应结果
   */
  private <T> UdsResult<T> genResult(UdsCode code, String message, T data) {
    UdsResult<T> result = new UdsResult<>();
    result.setCode(code);
    result.setMessage(message);
    result.setData(data);
    return result;
  }

  /**
   * 用于关闭建立的信号订阅长连接
   */
  @RequiredArgsConstructor
  public static class UdsConnection {

    private final Connection connection;

    /**
     * 关闭建立的信号订阅长连接
     */
    public void disconnectSub() {
      if (connection != null) {
        connection.disposeNow();
      }
    }
  }
}

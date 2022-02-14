package com.qgschina.udssdk.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.qgschina.udssdk.common.constant.InnerErrorCode;
import com.qgschina.udssdk.common.constant.InnerReqType;
import com.qgschina.udssdk.common.constant.InnerSubRespTypeCode;
import com.qgschina.udssdk.common.constant.ServiceType;
import com.qgschina.udssdk.common.exception.*;
import com.qgschina.udssdk.common.model.InnerReqData;
import com.qgschina.udssdk.common.model.InnerRespData;
import com.qgschina.udssdk.common.model.NamespaceResultData;
import com.qgschina.udssdk.common.model.NamespaceResultDataItem;
import com.qgschina.udssdk.common.util.Base64Utils;
import com.qgschina.udssdk.common.util.DataTypeUtils;
import com.qgschina.udssdk.common.util.JsonUtils;
import com.qgschina.udssdk.common.util.ReflectionUtils;
import com.qgschina.udssdk.server.annotation.UdsMethod;
import com.qgschina.udssdk.server.annotation.UdsService;
import com.qgschina.udssdk.server.annotation.UdsSignal;
import com.qgschina.udssdk.server.model.SignalMapItem;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.codec.json.JsonObjectDecoder;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultEventExecutor;
import lombok.extern.slf4j.Slf4j;
import reactor.netty.DisposableChannel;
import reactor.netty.DisposableServer;
import reactor.netty.NettyInbound;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpServer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 用于为 Client 提供服务
 */
@Slf4j
public class Server {

  /**
   * 用于启动 UDS 服务的 Socket 文件所在路径
   */
  private static String SOCKET_PATH = "";

  /**
   * JSON 支持的最大字节数, 单位 MB, 默认 1 MB
   */
  private static int MAX_BYTES_MB = 1;

  /**
   * 仅代表是否已配置完成
   */
  private static boolean CONFIGURED = false;

  /**
   * 存储所有能被"方法调用"的方法名, Key 为方法名
   */
  private final Map<String, SignalMapItem> methodMap = new HashMap<>();

  /**
   * 存储所有能被"信号触发"的信号名 (不包含"信号订阅"), Key 信号名
   */
  private final Map<String, SignalMapItem> signalMap = new HashMap<>();

  /**
   * 用于执行信号触发的线程池
   */
  private final Executor executor = Executors.newSingleThreadExecutor();

  /**
   * 可进行"信号订阅"的 Channel ID, Key 为信号名
   */
  private final Map<String, Set<ChannelId>> recipientMap = new HashMap<>();

  /**
   * 可进行"信号订阅"的 Channel 组
   */
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final ChannelGroup recipients = new DefaultChannelGroup(
      new DefaultEventExecutor());

  /**
   * 用于关闭 Server
   */
  private final DisposableServer server;

  private Server() {
    // 若 Socket 文件已被某个服务启动, 则说明命名空间已被启用或占用
    if (checkIfSocketFileUsed()) {
      throw new NamespaceOccupiedException("命名空间已被占用: " + SOCKET_PATH);
    }

    // 创建并启动 Server
    server = TcpServer.create()
        // Unix Domain Sockets (UDS)
        .bindAddress(() -> new DomainSocketAddress(SOCKET_PATH))
        // 开启 Wire Logger
        .wiretap(true)
        .doOnConnection(conn -> conn
            // 各语言间数据都是通过 JSON 字符串传递
            // 默认最大支持 1 MB 字节数
            .addHandler(new JsonObjectDecoder(1024 * 1024 * MAX_BYTES_MB)))
        .handle((in, out) -> in
            .receive()
            .asString(CharsetUtil.UTF_8)
            .log() // 记录 Log
            .map(jsonReq -> handleResp(in, jsonReq))
            .flatMap(out::sendObject))
        .bindNow();

    // 绑定 JVM Shutdown Hook
    // 突然关闭 JVM 不生效，如：`kill -9 <jvm_pid>`
    bindJvmShutdownHookForStopServer();
  }

  private void bindJvmShutdownHookForStopServer() {
    Thread haltedHook = new Thread(server::disposeNow);
    Runtime.getRuntime().addShutdownHook(haltedHook);
  }

  /**
   * 处理 Client 的请求
   *
   * @param in      Netty 入栈
   * @param jsonReq Client 提交的 JSON 字符串
   * @return 最终返回给 Client 的 Netty 字节缓冲区
   */
  private ByteBuf handleResp(NettyInbound in, String jsonReq) {
    try {
      // 获取请求数据对象
      InnerReqData reqData = parseReqData(jsonReq);

      // 内部写死, 用于错误回滚
      if (reqData.getType().equals("error")) {
        throw new UdsSdkException(reqData.getSignal());
      }

      // 若为方法调用, 则直接调用方法, 并返回方法的返回值
      if (reqData.getType()
          .equals(InnerReqType.CALL_METHOD.value())) {
        return callMethod(reqData);
      }

      // 若为信号触发, 则在线程池中调用方法, 且不返回方法返回值
      if (reqData.getType().equals(InnerReqType.SIGNAL.value())) {
        return triggerSignal(reqData);
      }

      // 若为信号订阅, 则加入订阅组, 并返回订阅成功的消息
      if (reqData.getType().equals(
          InnerReqType.SIGNAL_SUB.value())) {
        return addRecipient(in, reqData);
      }

      // 获取地址空间 - 获取可被调用的方法
      if (reqData.getType().equals(
          InnerReqType.GET_ALL_METHODS.value())) {
        return getAllMethods();
      }

      // 获取地址空间 - 获取可被触发或订阅的信号
      if (reqData.getType().equals(
          InnerReqType.GET_ALL_SIGNALS.value())) {
        return getAllSignals();
      }

      // 获取地址空间 - 获取可被调用的方法, 及可被触发或订阅的信号
      if (reqData.getType().equals(
          InnerReqType.GET_ALL_METHODS_SIGNALS.value())) {
        return getAllMethodsAndSignals();
      }

      // 其他请求类型, 直接拒绝
      return genHandledByteBuf(genRespData(InnerErrorCode.ERROR,
          "请求类型错误", null, null));
    } catch (UdsSdkException e) {
      log.warn("返回给 Client 的错误消息 --> " + e.getMessage());
      return genHandledByteBuf(genRespData(InnerErrorCode.ERROR,
          e.getMessage(), null, null));
    } catch (Exception e) {
      log.error("SDK Server 内部异常", e);
      return genHandledByteBuf(genRespData(InnerErrorCode.ERROR,
          "SDK Server 端内部异常: " + e.getMessage(),
          null, null));
    }
  }

  private static class SingletonHelper {

    private static final Server INSTANCE = new Server();
  }

  /**
   * 初始化 Server 配置
   * <p>
   * 注意: 该方法只会在 {@link Server#getInstance()} 前生效一次
   *
   * @param type       服务类型
   * @param namespace  服务子命名空间
   * @param maxBytesMb 允许数据传输的最大字节数, 单位: MB
   */
  public static void init(ServiceType type, String namespace, int maxBytesMb) {
    SOCKET_PATH = type.value() + "." + namespace;
    MAX_BYTES_MB = maxBytesMb;
    CONFIGURED = true;
  }

  /**
   * 获取 Server 单例对象
   * <p>
   * 必须先执行 {@link Server#init} 完成初始化配置
   *
   * @return Server 单例对象
   */
  public static Server getInstance() {
    if (!CONFIGURED) {
      throw new ConfigException("必须先执行 `Server#init` 完成初始化配置");
    }

    return SingletonHelper.INSTANCE;
  }

  /**
   * 注册服务
   *
   * <ul>
   *   <li>{@link UdsService}: 注解在类名上, 标识服务</li>
   *   <li>{@link UdsMethod}: 注解在方法上, 标识方法调用的方法名</li>
   *   <li>{@link UdsSignal}: 注解在方法上, 标识信号触发的信号名</li>
   * </ul>
   *
   * 注意: 若方法返回值存在 <b>{@code byte[]}</b>,
   * 则会被转为 Base64 字符串再给 Client
   *
   * @param service 拥有特定注解的服务实例化对象
   */
  public void registerService(Object service) {
    // 检查是否使用了 `UdsService` 注解
    checkIfUdsService(service);

    // 将打上了 `UdsMethod` 和 `UdsSignal` 注解的方法加入到 Map 中
    addMethodAndSignalMap(service);
  }

  /**
   * 注册信号订阅
   *
   * <ul>
   *   <li>Server 进行消息推送的信号名</li>
   *   <li>Client 进行信号订阅的信号名</li>
   * </ul>
   *
   * @param signal 注册为信号订阅的信号名
   */
  public void registerSubSignal(String signal) {
    if (recipientMap.containsKey(signal)) {
      throw new RegisterException("存在同名的信号订阅: " + signal);
    }

    recipientMap.put(signal, new HashSet<>());
  }

  /**
   * 消息推送
   * <p>
   * 推送消息给 Client
   * <p>
   * 注意: 若数据存在 <b>{@code byte[]}</b>, 则会被转为 Base64 字符串再给 Client
   *
   * @param signal 注册为信号订阅的信号
   * @param data   推送给 Client 的数据
   */
  public void send(String signal, Object data) {
    if (!recipientMap.containsKey(signal)) {
      throw new RegisterException("未注册信号: " + signal);
    }

    if (!JsonUtils.checkIfCanSerialize(data.getClass())) {
      throw new ParamException("数据类型错误");
    }

    InnerRespData<Object> respData = genRespData(InnerErrorCode.SUCCESS,
        "消息推送", InnerSubRespTypeCode.SUB_DATA, data);

    String jsonReq;
    try {
      jsonReq = JsonUtils.toJson(respData);
    } catch (JsonProcessingException e) {
      throw new DataProcessException("SDK 序列化失败", e);
    }

    recipients.writeAndFlush(Unpooled.copiedBuffer(jsonReq, CharsetUtil.UTF_8),
        channel -> recipientMap.get(signal).contains(channel.id()));
  }

  /**
   * 关闭服务
   * <p>
   * 注意: 通常 Server 都不是通过自身程序关闭的, 故执行本方法并不会关闭 JVM
   */
  public void stop() {
    server.disposeNow();
  }

  /**
   * 判断 Socket 文件是否已被使用
   *
   * @return {@code true} 若 Socket 文件已被使用
   */
  private boolean checkIfSocketFileUsed() {
    try {
      TcpClient.create()
          // Unix Domain Sockets (UDS)
          .remoteAddress(() -> new DomainSocketAddress(SOCKET_PATH))
          .doOnConnected(DisposableChannel::dispose)
          .connectNow();
      return true;
    } catch (Exception ignore) {
      return false;
    }
  }

  /**
   * 构造 Server 的响应对象
   *
   * @param error 内部错误码
   * @param msg   提示消息
   * @param type  内部 Server 端返回订阅类型
   * @param data  具体数据
   * @param <T>   具体数据的类型
   * @return 响应对象
   */
  private <T> InnerRespData<T> genRespData(InnerErrorCode error, String msg,
      InnerSubRespTypeCode type, T data) {
    InnerRespData<T> respData = new InnerRespData<>();
    respData.setCode(error.value());
    respData.setMsg(msg);
    respData.setData(data);

    if (type != null) {
      // 仅信号订阅才有该字段
      respData.setType(type.value());
    }

    return respData;
  }

  /**
   * 构造 Netty 字节缓冲区
   *
   * @param response Server 的响应对象
   * @return Netty 字节缓冲区
   */
  private ByteBuf genHandledByteBuf(InnerRespData<?> response) {
    return Unpooled.copiedBuffer(toJson(response), CharsetUtil.UTF_8);
  }

  /**
   * JSON 序列化+异常处理
   *
   * @param obj 需要被序列化的对象
   * @return JSON 字符串
   */
  private String toJson(Object obj) {
    try {
      return JsonUtils.toJson(obj);
    } catch (JsonProcessingException e) {
      log.error("SDK 序列化失败", e);
      InnerRespData<Object> respData = genRespData(InnerErrorCode.ERROR,
          "SDK 序列化失败", null, null);

      try {
        return JsonUtils.toJson(respData);
      } catch (JsonProcessingException e1) {
        log.error("SDK 内部数据序列化错误", e1);
        return "SDK 内部数据序列化错误";
      }
    }
  }

  /**
   * 解析请求的 JSON 字符串
   *
   * @param json JSON 字符串
   * @return JSON 反序列化后的请求数据对象
   * @throws JsonProcessingException JSON 反序列化失败
   */
  private InnerReqData parseReqData(String json)
      throws JsonProcessingException {
    return JsonUtils.parseJson(json, InnerReqData.class);
  }

  /**
   * 执行方法调用
   *
   * @param reqData 请求数据对象
   * @return Netty 节点缓冲区
   * @throws InvocationTargetException 通过反射调用方法失败
   * @throws IllegalAccessException    通过反射调用方法失败
   */
  private ByteBuf callMethod(InnerReqData reqData)
      throws InvocationTargetException, IllegalAccessException {
    if (!methodMap.containsKey(reqData.getMethod())) {
      throw new ParamException("方法名不存在");
    }

    Object result = invokeMethod(reqData, InvokeMethodType.METHOD);

    return genHandledByteBuf(genRespData(InnerErrorCode.SUCCESS,
        "方法调用成功", null, result));
  }

  /**
   * 执行信号触发 (异步调用方法)
   *
   * @param reqData 请求数据对象
   * @return Netty 字节缓冲区
   */
  private ByteBuf triggerSignal(InnerReqData reqData) {
    if (!signalMap.containsKey(reqData.getSignal())) {
      throw new ParamException("信号名不存在");
    }

    executor.execute(() -> {
      try {
        invokeMethod(reqData, InvokeMethodType.SIGNAL);
      } catch (InvocationTargetException | IllegalAccessException e) {
        log.error("反射调用方法失败", e);
      }
    });

    return genHandledByteBuf(genRespData(InnerErrorCode.SUCCESS,
        "信号触发成功", null, null));
  }

  /**
   * 利用反射机制调用某个对象的某个方法
   *
   * @param reqData 请求数据
   * @param type    方法调用类型
   * @return 方法调用成功后的返回结果
   * @throws InvocationTargetException 通过反射调用方法失败
   * @throws IllegalAccessException    通过反射调用方法失败
   */
  private Object invokeMethod(InnerReqData reqData, InvokeMethodType type)
      throws InvocationTargetException, IllegalAccessException {
    List<Object> params = reqData.getData();

    SignalMapItem item;
    if (type == InvokeMethodType.METHOD) {
      // 方法调用
      item = methodMap.get(reqData.getMethod());
    } else if (type == InvokeMethodType.SIGNAL) {
      // 信号触发
      item = signalMap.get(reqData.getSignal());
    } else {
      throw new ParamException("SDK 内部方法调用类型错误");
    }

    Method method = item.getMethod();
    // `byte[]` 数据是以 Base64 字符串的形式传输,
    // 故若方法的参数类型是 `byte[]`, 则需要先将 Base64 转为 `byte[]` 后才可调用
    try {
      convertParams(method, params);
    } catch (Exception e) {
      throw new ParamException("参数错误: " + e.getMessage());
    }

    try {
      return method.invoke(item.getService(), params.toArray());
    } catch (IllegalArgumentException e) {
      throw new ParamException("参数错误: " + e.getMessage());
    }
  }

  /**
   * 将传入参数转换为方法可识别的参数
   *
   * <ul>
   *   <li>若方法的接收参数为 {@code byte[]},
   *   则将对应参数 (Base64 字符串) 转为 {@code byte[]}</li>
   * </ul>
   *
   * @param method 反射的方法对象
   * @param params 调用方法时的参数列表
   * @throws JsonProcessingException JSON 序列化/反序列化失败
   */
  private void convertParams(Method method, List<Object> params)
      throws JsonProcessingException {
    Class<?>[] paramTypes = method.getParameterTypes();

    for (int i = 0; i < paramTypes.length; ++i) {
      if (DataTypeUtils.checkIfByteArray(paramTypes[i])) {
        String str = (String) params.get(i);
        params.set(i, Base64Utils.decode(str));
      } else {
        try {
          paramTypes[i].cast(params.get(i));
        } catch (ClassCastException ignore) {
          // POJO
          String json = JsonUtils.toJson(params.get(i));
          params.set(i, JsonUtils.parseJson(json, paramTypes[i]));
        }
      }
    }
  }

  /**
   * 加入信号订阅 Channel 和 Map
   *
   * @param in      Channel 入栈
   * @param reqData 请求数据
   * @return Netty 字节缓冲区
   */
  private ByteBuf addRecipient(NettyInbound in, InnerReqData reqData) {
    String signal = reqData.getSignal();

    if (recipientMap.containsKey(signal)) {
      in.withConnection(conn -> {
        Channel channel = conn.channel();
        recipientMap.get(signal).add(conn.channel().id());
        recipients.add(channel);
      });

      return genHandledByteBuf(genRespData(InnerErrorCode.SUCCESS,
          "信号订阅成功", InnerSubRespTypeCode.SUB_MSG, null));
    }

    return genHandledByteBuf(genRespData(InnerErrorCode.ERROR,
        "订阅失败: 没有该信号", InnerSubRespTypeCode.SUB_MSG, null));
  }

  /**
   * 获取所有可执行"方法调用"的方法
   *
   * @return Netty 字节缓冲区
   */
  private ByteBuf getAllMethods() {
    NamespaceResultData data = getAllMethodsOrSignals(InvokeMethodType.METHOD);

    return genHandledByteBuf(genRespData(InnerErrorCode.SUCCESS,
        "获取所有方法", null, data));
  }

  /**
   * 获取所有可执行"信号触发"或"信号订阅"的方法
   *
   * @return Netty 字节缓冲区
   */
  private ByteBuf getAllSignals() {
    NamespaceResultData data = getAllMethodsOrSignals(InvokeMethodType.SIGNAL);

    return genHandledByteBuf(genRespData(InnerErrorCode.SUCCESS,
        "获取所有信号 (信号触发+信号订阅)", null, data));
  }

  /**
   * 获取所有可执行方法或信号
   *
   * <ul>
   *   <li>所有可执行"方法调用"的方法</li>
   *   <li>所有可执行"信号触发"或"信号订阅"的信号</li>
   * </ul>
   *
   * @param type 方法调用类型
   * @return 命名空间数据结果对象
   */
  private NamespaceResultData getAllMethodsOrSignals(InvokeMethodType type) {
    List<NamespaceResultDataItem> dataItems = new ArrayList<>();

    Map<String, SignalMapItem> map;
    if (type == InvokeMethodType.METHOD) {
      // 方法调用
      map = methodMap;
    } else if (type == InvokeMethodType.SIGNAL) {
      // 信号触发
      map = signalMap;
    } else {
      throw new ParamException("SDK 内部方法信号类型错误");
    }

    for (Entry<String, SignalMapItem> entry : map.entrySet()) {
      Method method = entry.getValue().getMethod();

      NamespaceResultDataItem item = new NamespaceResultDataItem();
      item.setName(entry.getKey());
      item.setParameterNames(ReflectionUtils.getParamNames(method));
      item.setParameterTypes(ReflectionUtils.getParamTypes(method));

      dataItems.add(item);
    }

    if (type == InvokeMethodType.SIGNAL) {
      // 信号订阅
      for (Entry<String, Set<ChannelId>> entry : recipientMap
          .entrySet()) {
        NamespaceResultDataItem item = new NamespaceResultDataItem();
        item.setName(entry.getKey());

        dataItems.add(item);
      }
    }

    NamespaceResultData result = new NamespaceResultData();

    if (type == InvokeMethodType.METHOD) {
      // 方法调用
      result.setMethod(dataItems);
    } else {
      // 信号触发
      result.setSignal(dataItems);
    }

    result.setMethodNum(dataItems.size());

    return result;
  }

  /**
   * 获取可被调用的方法, 及可被触发或订阅的信号
   *
   * @return Netty 字节缓冲区
   */
  private ByteBuf getAllMethodsAndSignals() {
    NamespaceResultData method = getAllMethodsOrSignals(
        InvokeMethodType.METHOD);
    NamespaceResultData signal = getAllMethodsOrSignals(
        InvokeMethodType.SIGNAL);

    NamespaceResultData data = new NamespaceResultData();
    data.setMethod(method.getMethod());
    data.setMethodNum(method.getMethodNum());
    data.setSignal(signal.getSignal());

    return genHandledByteBuf(genRespData(InnerErrorCode.SUCCESS,
        "所有方法和信号", null, data));
  }

  /**
   * 检查是否使用了 {@link UdsService} 注解
   *
   * @param service 需要判断的服务对象
   */
  private void checkIfUdsService(Object service) {
    if (Objects.isNull(service)) {
      throw new RegisterException("传入对象不能为空");
    }

    if (!service.getClass().isAnnotationPresent(UdsService.class)) {
      throw new RegisterException("未使用 `UdsService` 注解");
    }
  }

  /**
   * 将打上了 {@code UdsMethod} 或 {@code UdsSignal} 注解的方法加入到 Map 中
   *
   * @param service 服务对象
   */
  private void addMethodAndSignalMap(Object service) {
    Class<?> clazz = service.getClass();

    for (Method method : clazz.getDeclaredMethods()) {
      // 加入方法调用
      if (method.isAnnotationPresent(UdsMethod.class)) {
        // 参数及返回值类型检查
        checkParameterAndReturnType(method);

        UdsMethod methodAnn = method.getAnnotation(UdsMethod.class);
        String methodName = methodAnn.value();

        if (methodMap.containsKey(methodName)) {
          throw new RegisterException("存在同名的方法调用: " + methodName);
        }

        addRegisterMap(methodMap, service, method, methodName);
      }

      // 加入信号触发
      if (method.isAnnotationPresent(UdsSignal.class)) {
        // 参数及返回值类型检查
        checkParameterAndReturnType(method);

        UdsSignal signalAnn = method.getAnnotation(UdsSignal.class);
        String signalName = signalAnn.value();

        if (signalMap.containsKey(signalName)) {
          throw new RegisterException("存在同名的信号触发: " + signalName);
        }

        addRegisterMap(signalMap, service, method, signalName);
      }
    }
  }

  /**
   * 参数及返回值类型检查
   *
   * @param method 方法对象
   */
  private void checkParameterAndReturnType(Method method) {
    Class<?> returnType = method.getReturnType();
    Optional<String> genReturnType =
        DataTypeUtils.getGeneralParamTypeName(returnType);
    if (!genReturnType.isPresent()) {
      throw new ParamException("方法返回值类型不支持: " + returnType.getSimpleName());
    }

    for (Class<?> type : method.getParameterTypes()) {
      Optional<String> paramType = DataTypeUtils.getGeneralParamTypeName(type);
      if (!paramType.isPresent()) {
        throw new ParamException("方法参数类型不支持: " + type.getSimpleName());
      }
    }
  }

  /**
   * 将"方法调用"或"信号触发"加入到对应的 Map 中
   *
   * @param map     Map
   * @param service 服务对象
   * @param method  方法对象
   * @param key     Map Key
   */
  private void addRegisterMap(Map<String, SignalMapItem> map,
      Object service, Method method, String key) {
    SignalMapItem item = new SignalMapItem();
    item.setService(service);
    item.setMethod(method);
    map.put(key, item);
  }

  /**
   * 方法调用类型
   */
  private enum InvokeMethodType {
    METHOD, SIGNAL
  }
}

package com.qgschina.udssdk.test.service;

import com.qgschina.udssdk.server.annotation.UdsMethod;
import com.qgschina.udssdk.server.annotation.UdsService;
import com.qgschina.udssdk.server.annotation.UdsSignal;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 使用 {@link UdsService} 注解, 标注该对象为一个服务对象
 */
@UdsService
public class TestService {

  /**
   * 注册方法为"方法调用"
   *
   * <ul>
   *   <li>使用 {@link UdsMethod} 注解, 标注该方法为"方法调用"</li>
   *   <li>Client 可通过方法名 {@code echo} 调用该方法, 并得到返回值</li>
   * </ul>
   *
   * 注意: 若返回值存在 <b>{@code byte[]}</b>, 则会被转为 Base64 字符串再给 Client
   *
   * @param bytes 字节数组
   * @param list 列表
   * @return Map, 包含字节数组
   */
  @UdsMethod("echo")
  public Map<String, Object> echoMsg(byte[] bytes,
      List<Map<String, Object>> list) {
    HashMap<String, Object> map = new HashMap<String, Object>() {{
      put("byteArray", new HashMap<String, Object>() {{
        put("bytes", bytes);
        put("string", new String(bytes));
      }});
      put("list", list);
    }};

    printMsg(map);

    return map;
  }

  /**
   * 注册方法为"信号触发"
   *
   * <ul>
   *   <li>使用 {@link UdsSignal} 注解, 标注该方法为"信号触发"</li>
   *   <li>Client 可通过信号名 {@code trigger} 异步调用该方法, 且无需返回值</li>
   * </ul>
   *
   * @param art POJO
   * @param score 浮点数
   * @throws InterruptedException 可忽略
   */
  @UdsSignal("trigger")
  public void fire(Artifact art, double score) throws InterruptedException {
    // 模拟 2 秒耗时操作
    Thread.sleep(2 * 1000);

    System.out.printf("信号触发 --> 工件: %s, 版本: %d, 评分: %f\n",
        art.getName(), art.getVersion(), score);
  }

  /**
   * 未使用 {@link UdsMethod} 或 {@link UdsSignal} 注解, 故 Client 不可调用该方法
   *
   * @param map Map
   */
  public void printMsg(Map<String, Object> map) {
    System.out.println("方法调用 --> " + map);
  }

  @Data
  public static class Artifact {

    private String name;
    private Integer version;
  }
}

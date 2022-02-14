package com.qgschina.udssdk.common.util;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 工具类
 */
public class JsonUtils {

  /**
   * 获取配置后的 Jackson 的 {@link ObjectMapper}
   *
   * @return 配置后的 {@link ObjectMapper}
   */
  public static ObjectMapper getObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setSerializationInclusion(Include.NON_NULL);
    return mapper;
  }

  /**
   * 将 Java 对象序列化为 JSON 字符串
   *
   * @param obj Java 对象
   * @return JSON 字符串
   * @throws JsonProcessingException JSON 序列化失败
   */
  public static String toJson(Object obj) throws JsonProcessingException {
    return getObjectMapper().writeValueAsString(obj);
  }

  /**
   * 将 JSON 字符串反序列化为 Java 对象
   *
   * @param json  JSON 字符串
   * @param clazz JSON 字符串反序列化后的对象类类型
   * @param <T>   Java 对象的具体类型
   * @return Java 对象
   * @throws JsonProcessingException JSON 反序列化失败
   */
  public static <T> T parseJson(String json, Class<T> clazz)
      throws JsonProcessingException {
    return getObjectMapper().readValue(json, clazz);
  }

  /**
   * 将 JSON 字符串反序列化为 Java 对象
   *
   * @param json JSON 字符串
   * @param ref  支持泛型反序列化的类型引用对象
   * @param <T>  Java 对象的具体类型
   * @return Java 对象
   * @throws JsonProcessingException JSON 反序列化失败
   */
  public static <T> T parseJson(String json, TypeReference<T> ref)
      throws JsonProcessingException {
    return getObjectMapper().readValue(json, ref);
  }

  /**
   * 判断该对象是否可被 JSON 序列化
   *
   * @param clazz 对象类类型
   * @return {@code true} 若该对象可被 JSON 序列化
   */
  public static boolean checkIfCanSerialize(Class<?> clazz) {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.canSerialize(clazz);
  }
}
